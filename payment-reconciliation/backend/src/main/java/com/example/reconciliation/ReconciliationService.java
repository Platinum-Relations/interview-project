package com.example.reconciliation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReconciliationService {
    private static final TypeReference<List<Map<String,Object>>> LIST_MAP = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ReconciliationService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    private String state(String key, String fallback) {
        List<String> values = jdbc.query("SELECT state_value FROM app_state WHERE state_key=?", (rs, n) -> rs.getString(1), key);
        return values.isEmpty() ? fallback : values.get(0);
    }
    private void save(String key, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            jdbc.update("MERGE INTO app_state (state_key,state_value,updated_at) KEY(state_key) VALUES (?,?,CURRENT_TIMESTAMP)", key, json);
        } catch (Exception e) { throw new IllegalStateException("Could not persist application state", e); }
    }
    private List<Map<String,Object>> readList(String key) {
        try { return mapper.readValue(state(key, "[]"), LIST_MAP); }
        catch (Exception e) { throw new IllegalStateException("Stored application data could not be read", e); }
    }
    private Map<String,Object> readMap(String key) {
        try { return mapper.readValue(state(key, "{}"), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Stored application data could not be read", e); }
    }
    private String str(Map<String,Object> m, String k) { Object v=m.get(k); return v==null?"":String.valueOf(v).trim(); }
    private BigDecimal decimal(Map<String,Object> m, String k) {
        try { return new BigDecimal(str(m,k)); } catch (Exception e) { throw new IllegalArgumentException("Invalid or missing numeric field: " + k); }
    }
    private BigDecimal money(BigDecimal n) { return n.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal zero() { return BigDecimal.ZERO.setScale(2); }
    private boolean blank(String s) { return s == null || s.isBlank(); }

    @Transactional
    public Map<String,Object> importInternal(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty CSV file.");
        List<Map<String,Object>> rows = new ArrayList<>(); List<Map<String,Object>> errors = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new String(file.getBytes(), StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build())) {
            for (CSVRecord record : parser) {
                try {
                    Map<String,Object> r = new LinkedHashMap<>(); record.toMap().forEach((k,v)->r.put(k.trim(),v.trim()));
                    for (String f : List.of("internal_txn_id","merchant_id","card_type","card_last4","gross_amount","currency","type","captured_at")) if (blank(str(r,f))) throw new IllegalArgumentException("Missing field: "+f);
                    decimal(r,"gross_amount");
                    if (!Set.of("USD").contains(str(r,"currency"))) throw new IllegalArgumentException("Unsupported currency: "+str(r,"currency"));
                    if (!Set.of("VISA","MASTERCARD","AMEX","DISCOVER").contains(str(r,"card_type").toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Unsupported card type: "+str(r,"card_type"));
                    if (!Set.of("SALE","REFUND").contains(str(r,"type").toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("type must be SALE or REFUND");
                    r.put("type",str(r,"type").toUpperCase(Locale.ROOT)); rows.add(r);
                } catch (Exception ex) { errors.add(Map.of("row",record.getRecordNumber(),"reason",ex.getMessage()==null?"Invalid row":ex.getMessage())); }
            }
        } catch (IllegalArgumentException e) { throw e; } catch (Exception e) { throw new IllegalArgumentException("Could not parse CSV. Ensure it has a header row and valid CSV syntax."); }
        save("internal", rows); save("internalErrors", errors); save("lastInternalImport", Map.of("filename",safeFilename(file.getOriginalFilename()),"accepted",rows.size(),"quarantined",errors.size(),"at",Instant.now().toString()));
        save("latestRun", Map.of());
        return Map.of("accepted",rows.size(),"quarantined",errors.size(),"errors",errors,"message","Internal transactions imported.");
    }
    @Transactional
    public Map<String,Object> importSettlements(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty JSON file.");
        List<Map<String,Object>> rows=new ArrayList<>(); List<Map<String,Object>> errors=new ArrayList<>();
        List<Map<String,Object>> parsed;
        try { parsed=mapper.readValue(file.getBytes(),LIST_MAP); } catch(Exception e) { throw new IllegalArgumentException("Settlement file must be a JSON array of objects."); }
        int rowNum=0;
        for (Map<String,Object> source:parsed) { rowNum++; try {
            Map<String,Object> r=new LinkedHashMap<>(source);
            for(String f:List.of("network_ref","merchant_id","card_last4","card_type","settled_amount","interchange_fee","processor_fee","currency","settlement_date")) if(blank(str(r,f))) throw new IllegalArgumentException("Missing field: "+f);
            decimal(r,"settled_amount"); decimal(r,"interchange_fee"); decimal(r,"processor_fee");
            if(!"USD".equals(str(r,"currency"))) throw new IllegalArgumentException("Unsupported currency: "+str(r,"currency"));
            if(!Set.of("VISA","MASTERCARD","AMEX","DISCOVER").contains(str(r,"card_type").toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Unsupported card type: "+str(r,"card_type"));
            rows.add(r);
        } catch(Exception ex) { errors.add(Map.of("row",rowNum,"reason",ex.getMessage()==null?"Invalid row":ex.getMessage())); } }
        save("settlements",rows); save("settlementErrors",errors); save("lastSettlementImport",Map.of("filename",safeFilename(file.getOriginalFilename()),"accepted",rows.size(),"quarantined",errors.size(),"at",Instant.now().toString()));
        save("latestRun",Map.of());
        return Map.of("accepted",rows.size(),"quarantined",errors.size(),"errors",errors,"message","Settlement records imported.");
    }
    private String safeFilename(String name) { return name==null?"upload":name.replaceAll("[\\r\\n]",""); }

    private BigDecimal[] expectedFees(Map<String,Object> txn) {
        String card=str(txn,"card_type").toUpperCase(Locale.ROOT);
        BigDecimal pct, flat;
        switch(card) {
            case "VISA" -> {pct=new BigDecimal("0.018");flat=new BigDecimal("0.10");}
            case "MASTERCARD" -> {pct=new BigDecimal("0.019");flat=new BigDecimal("0.10");}
            case "AMEX" -> {pct=new BigDecimal("0.025");flat=new BigDecimal("0.15");}
            case "DISCOVER" -> {pct=new BigDecimal("0.020");flat=new BigDecimal("0.10");}
            default -> throw new IllegalArgumentException("Unsupported card type: "+card);
        }
        BigDecimal gross=decimal(txn,"gross_amount");
        if("REFUND".equals(str(txn,"type"))) return new BigDecimal[]{zero(),zero(),gross.setScale(2,RoundingMode.HALF_UP)};
        BigDecimal interchange=money(gross.multiply(pct).add(flat));
        BigDecimal processor=money(gross.multiply(new BigDecimal("0.003")).add(new BigDecimal("0.05")));
        return new BigDecimal[]{interchange,processor,money(gross.subtract(interchange).subtract(processor))};
    }
    private boolean isRefund(Map<String,Object> txn) { return "REFUND".equalsIgnoreCase(str(txn,"type")) || decimal(txn,"gross_amount").signum()<0; }
    private boolean sameSign(Map<String,Object> a, Map<String,Object> b) { return decimal(a,"gross_amount").signum()==decimal(b,"settled_amount").signum(); }
    private boolean close(BigDecimal a, BigDecimal b) { return a.subtract(b).abs().compareTo(new BigDecimal("0.02"))<=0; }

    @Transactional
    public Map<String,Object> reconcile() {
        List<Map<String,Object>> internal=readList("internal"), settlements=readList("settlements");
        if(internal.isEmpty() && settlements.isEmpty()) throw new IllegalArgumentException("Import internal transactions and settlement records before reconciling.");
        String runId=UUID.randomUUID().toString(); Set<Integer> used=new HashSet<>(); List<Map<String,Object>> results=new ArrayList<>();
        Set<String> saleRefs=internal.stream().filter(t->"SALE".equalsIgnoreCase(str(t,"type")) && !blank(str(t,"merchant_ref"))).map(t->str(t,"merchant_ref")).collect(Collectors.toSet());
        for(Map<String,Object> txn:internal) {
            String type=str(txn,"type").toUpperCase(Locale.ROOT); BigDecimal[] fees;
            try { fees=expectedFees(txn); } catch(Exception e) { continue; }
            if("REFUND".equals(type) && !blank(str(txn,"merchant_ref")) && !saleRefs.contains(str(txn,"merchant_ref"))) {
                results.add(result("ORPHAN_REFUND",txn,null,"Refund reference has no originating SALE in the internal ledger.",decimal(txn,"gross_amount"),zero()));
            }
            List<Integer> candidates=new ArrayList<>();
            String ref=str(txn,"merchant_ref");
            if(!blank(ref)) for(int i=0;i<settlements.size();i++) { Map<String,Object>s=settlements.get(i); if(!used.contains(i)&&ref.equals(str(s,"merchant_ref"))&&str(txn,"merchant_id").equals(str(s,"merchant_id"))&&sameSign(txn,s)) candidates.add(i); }
            if(candidates.isEmpty()) for(int i=0;i<settlements.size();i++) { Map<String,Object>s=settlements.get(i); if(used.contains(i)||!blank(str(s,"merchant_ref"))||!str(txn,"merchant_id").equals(str(s,"merchant_id"))||!str(txn,"card_last4").equals(str(s,"card_last4"))||!str(txn,"card_type").equalsIgnoreCase(str(s,"card_type"))||!str(txn,"currency").equals(str(s,"currency"))||!sameSign(txn,s)) continue; if(close(decimal(s,"settled_amount"),fees[2])) candidates.add(i); }
            if(candidates.isEmpty()) { results.add(result("UNMATCHED_INTERNAL",txn,null,"No settlement candidate found within the reference and fallback matching rules.",decimal(txn,"gross_amount"),fees[2])); continue; }
            if(candidates.size()>1) {
                int firstCandidate = candidates.get(0);
                BigDecimal firstCandidateNet = decimal(settlements.get(firstCandidate), "settled_amount");
                boolean repeatedNet = candidates.stream().map(settlements::get).allMatch(s -> close(decimal(s, "settled_amount"), firstCandidateNet));
                if (repeatedNet) candidates = new ArrayList<>(List.of(firstCandidate));
                else { results.add(result("AMBIGUOUS_MATCH",txn,null,"Multiple settlement candidates matched; review manually.",decimal(txn,"gross_amount"),fees[2])); continue; }
            }
            int idx=candidates.get(0); Map<String,Object> settlement=settlements.get(idx); used.add(idx);
            BigDecimal reportedInterchange=decimal(settlement,"interchange_fee"), reportedProcessor=decimal(settlement,"processor_fee"), settled=decimal(settlement,"settled_amount");
            BigDecimal expectedUsingReported=money(decimal(txn,"gross_amount").subtract(reportedInterchange).subtract(reportedProcessor));
            if(isRefund(txn)) expectedUsingReported=decimal(txn,"gross_amount").setScale(2,RoundingMode.HALF_UP);
            if(!close(settled,expectedUsingReported)) results.add(result("AMOUNT_MISMATCH",txn,settlement,"Settlement amount is inconsistent with gross amount minus processor-reported fees.",expectedUsingReported,settled));
            if(!close(reportedInterchange,fees[0])||!close(reportedProcessor,fees[1])) results.add(result("FEE_DISCREPANCY",txn,settlement,"Reported fees differ from the published fee schedule.",fees[0].add(fees[1]),reportedInterchange.add(reportedProcessor)));
            if(close(settled,expectedUsingReported)&&close(reportedInterchange,fees[0])&&close(reportedProcessor,fees[1])) results.add(result("MATCHED",txn,settlement,"Transaction reconciled successfully.",fees[2],settled));
        }
        for(int i=0;i<settlements.size();i++) if(!used.contains(i)) {
            Map<String,Object>s=settlements.get(i);
            boolean duplicate=settlements.subList(0,i).stream().anyMatch(prev->str(prev,"merchant_id").equals(str(s,"merchant_id"))&&!blank(str(s,"merchant_ref"))&&str(prev,"merchant_ref").equals(str(s,"merchant_ref"))&&close(decimal(prev,"settled_amount"),decimal(s,"settled_amount"))&&sameSettlementSign(prev,s));
            results.add(result(duplicate?"DUPLICATE_SETTLEMENT":"UNMATCHED_SETTLEMENT",null,s,duplicate?"Repeated settlement appears to repeat a prior settled amount.":"Processor settlement has no matched internal transaction.",zero(),decimal(s,"settled_amount")));
        }
        Map<String,Object> run=new LinkedHashMap<>(); run.put("runId",runId); run.put("createdAt",Instant.now().toString()); run.put("results",results); run.put("internalCount",internal.size()); run.put("settlementCount",settlements.size());
        run.put("quarantinedInternal",readList("internalErrors").size()); run.put("quarantinedSettlements",readList("settlementErrors").size());
        save("run:"+runId,run); save("latestRun",run);
        return summary(run);
    }
    private boolean sameSettlementSign(Map<String,Object>a,Map<String,Object>b) { return decimal(a,"settled_amount").signum()==decimal(b,"settled_amount").signum(); }
    private Map<String,Object> result(String category,Map<String,Object> txn,Map<String,Object> settlement,String reason,BigDecimal expected,BigDecimal actual) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("id",UUID.randomUUID().toString());r.put("category",category);r.put("internal",txn);r.put("settlement",settlement);r.put("reason",reason);r.put("expectedAmount",expected);r.put("actualAmount",actual);r.put("difference",money(actual.subtract(expected)));r.put("merchantId",txn!=null?str(txn,"merchant_id"):settlement==null?"":str(settlement,"merchant_id"));return r;
    }
    private Map<String,Object> summary(Map<String,Object> run) {
        List<Map<String,Object>> results=(List<Map<String,Object>>)run.getOrDefault("results",List.of()); Map<String,Object> categories=new TreeMap<>();
        for(Map<String,Object> r:results) { String c=str(r,"category"); Map<String,Object> cat=(Map<String,Object>)categories.computeIfAbsent(c,k->{Map<String,Object>x=new LinkedHashMap<>();x.put("count",0);x.put("amount",zero());return x;}); cat.put("count",((Number)cat.get("count")).intValue()+1); cat.put("amount",money(new BigDecimal(String.valueOf(cat.get("amount"))).add(new BigDecimal(String.valueOf(r.get("difference"))).abs()))); }
        long matched=results.stream().filter(r->"MATCHED".equals(str(r,"category"))).count(); BigDecimal expected=zero(), actual=zero(), fees=zero();
        for(Map<String,Object> t:readList("internal")) try { expected=expected.add(expectedFees(t)[2]); } catch(Exception ignored) {}
        for(Map<String,Object> s:readList("settlements")) { try { actual=actual.add(decimal(s,"settled_amount"));fees=fees.add(decimal(s,"interchange_fee")).add(decimal(s,"processor_fee")); } catch(Exception ignored) {} }
        Map<String,Object> out=new LinkedHashMap<>(run); out.remove("results");out.put("categories",categories);out.put("matchedCleanly",matched);out.put("breakCount",results.stream().filter(r->!"MATCHED".equals(str(r,"category"))).count());out.put("breaks",results.stream().filter(r->!"MATCHED".equals(str(r,"category"))).toList());out.put("results",results);out.put("expectedPayout",money(expected));out.put("actualSettled",money(actual));out.put("payoutDifference",money(actual.subtract(expected)));out.put("totalFees",money(fees));return out;
    }
    public Map<String,Object> latestResults() { Map<String,Object> run=readMap("latestRun"); if(run.isEmpty()) return Map.of("hasRun",false,"message","No reconciliation run yet."); Map<String,Object> out=summary(run);out.put("hasRun",true);return out; }
    public Map<String,Object> runById(String id) { Map<String,Object> run=readMap("run:"+id);if(run.isEmpty())throw new IllegalArgumentException("Reconciliation run not found.");return summary(run); }
    public Map<String,Object> dashboard() { Map<String,Object> latest=latestResults(); Map<String,Object> imports=importStatus();Map<String,Object> out=new LinkedHashMap<>(latest);out.put("imports",imports);out.put("internalCount",readList("internal").size());out.put("settlementCount",readList("settlements").size());return out; }
    public Map<String,Object> importStatus() { return Map.of("internal",readMap("lastInternalImport"),"settlements",readMap("lastSettlementImport"),"internalErrors",readList("internalErrors"),"settlementErrors",readList("settlementErrors")); }
    public Map<String,Object> breaks(String runId,String category,String merchant,int page,int size) {
        if(page<0||size<1||size>200)throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 200.");
        Map<String,Object> run=readMap("run:"+runId);if(run.isEmpty())throw new IllegalArgumentException("Reconciliation run not found.");
        List<Map<String,Object>> list=((List<Map<String,Object>>)run.getOrDefault("results",List.of())).stream().filter(r->!"MATCHED".equals(str(r,"category"))).filter(r->blank(category)||category.equalsIgnoreCase(str(r,"category"))).filter(r->blank(merchant)||str(r,"merchantId").toLowerCase(Locale.ROOT).contains(merchant.toLowerCase(Locale.ROOT))).toList();
        int start=Math.min(page*size,list.size()),end=Math.min(start+size,list.size());return Map.of("items",list.subList(start,end),"page",page,"size",size,"total",list.size(),"pages",(int)Math.ceil(list.size()/(double)size));
    }
    public Map<String,Object> merchants(String runId) {
        Map<String,Object> run=readMap("run:"+runId);if(run.isEmpty())throw new IllegalArgumentException("Reconciliation run not found.");Map<String,Map<String,Object>> grouped=new TreeMap<>();
        for(Map<String,Object> r:(List<Map<String,Object>>)run.getOrDefault("results",List.of())) { String id=str(r,"merchantId");if(blank(id))id="Unknown";Map<String,Object> m=grouped.computeIfAbsent(id,k->{Map<String,Object>x=new LinkedHashMap<>();x.put("merchantId",k);x.put("matched",0);x.put("breaks",0);x.put("breakAmount",zero());return x;});if("MATCHED".equals(str(r,"category")))m.put("matched",((Number)m.get("matched")).intValue()+1);else {m.put("breaks",((Number)m.get("breaks")).intValue()+1);m.put("breakAmount",money(new BigDecimal(String.valueOf(m.get("breakAmount"))).add(new BigDecimal(String.valueOf(r.get("difference"))).abs())));}}
        return Map.of("items",grouped.values());
    }
}
