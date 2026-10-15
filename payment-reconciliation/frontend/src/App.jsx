import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { Activity, AlertTriangle, ArrowDownToLine, ArrowLeftRight, BadgeCheck, Banknote, ChevronDown, CircleHelp, FileCheck2, FileJson, FileSpreadsheet, Filter, LayoutDashboard, RefreshCw, Search, ShieldCheck, Upload, WalletCards } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1' });
const money = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const date = value => value ? new Date(value).toLocaleString() : '—';
const categories = ['ALL', 'UNMATCHED_INTERNAL', 'UNMATCHED_SETTLEMENT', 'AMOUNT_MISMATCH', 'FEE_DISCREPANCY', 'DUPLICATE_SETTLEMENT', 'ORPHAN_REFUND', 'AMBIGUOUS_MATCH', 'MALFORMED_ROW'];
const shortCategory = value => ({ MATCHED: 'Matched', UNMATCHED_INTERNAL: 'Unmatched internal', UNMATCHED_SETTLEMENT: 'Unmatched settlement', AMOUNT_MISMATCH: 'Amount mismatch', FEE_DISCREPANCY: 'Fee discrepancy', DUPLICATE_SETTLEMENT: 'Duplicate settlement', ORPHAN_REFUND: 'Orphan refund', AMBIGUOUS_MATCH: 'Ambiguous match', MALFORMED_ROW: 'Malformed row' }[value] || value || 'Unknown');

function App() {
  const [page, setPage] = useState('Overview');
  const [dashboard, setDashboard] = useState({});
  const [run, setRun] = useState(null);
  const [imports, setImports] = useState({});
  const [merchantRows, setMerchantRows] = useState([]);
  const [breakRows, setBreakRows] = useState([]);
  const [filter, setFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [internalFile, setInternalFile] = useState(null);
  const [settlementFile, setSettlementFile] = useState(null);
  const [health, setHealth] = useState(false);

  async function refresh() {
    try {
      const [d, i, h] = await Promise.all([api.get('/dashboard'), api.get('/imports'), api.get('/health')]);
      setDashboard(d.data); setImports(i.data); setHealth(h.data.status === 'UP');
      if (d.data.hasRun) { setRun(d.data); loadBreaks(d.data.runId, filter); loadMerchants(d.data.runId); }
      else { setRun(null); setBreakRows([]); setMerchantRows([]); }
    } catch (e) { setHealth(false); setError(e.response?.data?.error || 'Cannot connect to the API. Start the Java backend on port 8080.'); }
  }
  async function loadBreaks(runId, selected = filter) {
    if (!runId) return;
    try { const params = selected === 'ALL' ? {} : { category: selected }; const r = await api.get(`/reconciliations/${runId}/breaks`, { params }); setBreakRows(r.data.items || []); }
    catch (e) { setError(e.response?.data?.error || 'Unable to load discrepancies.'); }
  }
  async function loadMerchants(runId) { try { const r = await api.get(`/reconciliations/${runId}/merchants`); setMerchantRows(r.data.items || []); } catch { setMerchantRows([]); } }
  useEffect(() => { refresh(); }, []);
  useEffect(() => { if (run?.runId) loadBreaks(run.runId, filter); }, [filter, run?.runId]);

  async function upload(kind, file) {
    if (!file) { setError(`Choose a ${kind === 'internal-transactions' ? 'CSV' : 'JSON'} file first.`); return; }
    setBusy(true); setError(''); setMessage('');
    try {
      const body = new FormData(); body.append('file', file);
      const res = await api.post(`/imports/${kind}`, body, { headers: { 'Content-Type': 'multipart/form-data' } });
      setMessage(`${res.data.message} Accepted: ${res.data.accepted}; quarantined: ${res.data.quarantined}.`);
      await refresh();
    } catch (e) { setError(e.response?.data?.error || 'Import failed. Check the file format and backend logs.'); }
    finally { setBusy(false); }
  }
  async function reconcile() {
    setBusy(true); setError(''); setMessage('');
    try { const r = await api.post('/reconciliations'); setRun(r.data); setDashboard(r.data); await loadBreaks(r.data.runId, filter); await loadMerchants(r.data.runId); setMessage('Reconciliation completed and persisted.'); setPage('Overview'); }
    catch (e) { setError(e.response?.data?.error || 'Reconciliation failed. Import both datasets and try again.'); }
    finally { setBusy(false); }
  }
  const visibleBreaks = useMemo(() => breakRows.filter(r => `${r.merchantId || ''} ${r.reason || ''} ${r.category || ''} ${r.internal?.merchant_ref || ''} ${r.settlement?.network_ref || ''}`.toLowerCase().includes(search.toLowerCase())), [breakRows, search]);
  const chartData = useMemo(() => Object.entries(run?.categories || {}).filter(([k]) => k !== 'MATCHED').map(([category, val]) => ({ category: shortCategory(category), count: val.count })), [run]);
  const nav = [{ name: 'Overview', icon: LayoutDashboard }, { name: 'Import data', icon: Upload }, { name: 'Break investigation', icon: AlertTriangle }, { name: 'Merchants', icon: WalletCards }, { name: 'Run history', icon: Activity }];

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><ArrowLeftRight size={22}/></div><div><strong>settlewise</strong><span>PAYMENTS OPERATIONS</span></div></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav>{nav.map(item => <button key={item.name} onClick={() => setPage(item.name)} className={`nav-item ${page === item.name ? 'active' : ''}`}><item.icon size={18}/><span>{item.name}</span>{item.name === 'Break investigation' && run?.breakCount > 0 && <b className="nav-count">{run.breakCount}</b>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="secure-icon"><ShieldCheck size={18}/></div><div><strong>Secure workspace</strong><span>Local development mode</span></div></div>
    </aside>
    <main className="main-area">
      <header className="topbar"><div className="crumb">Payments <span>/</span> <strong>{page}</strong></div><div className="topbar-right"><span className={`api-status ${health ? 'online' : ''}`}><i/>{health ? 'API connected' : 'API disconnected'}</span><div className="avatar">PO</div></div></header>
      <div className="page-content">
        {error && <div className="alert error"><AlertTriangle size={18}/><span>{error}</span><button onClick={() => setError('')}>Dismiss</button></div>}
        {message && <div className="alert success"><BadgeCheck size={18}/><span>{message}</span><button onClick={() => setMessage('')}>Dismiss</button></div>}
        {page === 'Overview' && <>
          <div className="page-heading"><div><div className="eyebrow">RECONCILIATION CENTER</div><h1>Good evening, Operations</h1><p>Monitor settlement health and investigate payment exceptions.</p></div><button className="button secondary" onClick={refresh}><RefreshCw size={16}/> Refresh</button></div>
          {!run && <div className="welcome-panel"><div className="welcome-icon"><ArrowLeftRight size={25}/></div><div><h2>Start your first reconciliation</h2><p>Import your internal transaction CSV and processor settlement JSON to compare expected payouts, fees, and settlement records.</p><button className="button primary" onClick={() => setPage('Import data')}><Upload size={16}/> Import datasets</button></div></div>}
          <div className="metric-grid">
            <Metric title="Clean matches" value={run?.matchedCleanly ?? '—'} note="Transactions reconciled" icon={BadgeCheck} tone="green"/>
            <Metric title="Open discrepancies" value={run?.breakCount ?? '—'} note="Records requiring review" icon={AlertTriangle} tone="orange"/>
            <Metric title="Expected payout" value={run ? money(run.expectedPayout) : '—'} note="After scheduled fees" icon={Banknote} tone="blue"/>
            <Metric title="Total fees deducted" value={run ? money(run.totalFees) : '—'} note="Processor-reported fees" icon={WalletCards} tone="purple"/>
          </div>
          <div className="two-col"><section className="panel"><div className="panel-head"><div><h2>Discrepancy breakdown</h2><p>Exceptions grouped by category</p></div><button className="icon-button" onClick={() => setPage('Break investigation')}><ChevronDown size={17}/></button></div>{chartData.length ? <div className="chart-wrap"><ResponsiveContainer width="100%" height={260}><BarChart data={chartData} margin={{ top: 8, right: 8, left: -22, bottom: 50 }}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e9edf3"/><XAxis dataKey="category" tick={{fontSize:10,fill:'#667085'}} angle={-25} textAnchor="end" interval={0}/><YAxis allowDecimals={false} tick={{fontSize:11,fill:'#667085'}}/><Tooltip/><Bar dataKey="count" fill="#5267d9" radius={[5,5,0,0]} maxBarSize={40}/></BarChart></ResponsiveContainer></div> : <Empty title="No reconciliation data yet" text="Import both files and run reconciliation to populate this chart."/>}</section>
            <section className="panel"><div className="panel-head"><div><h2>Payout comparison</h2><p>Expected against processor settlements</p></div><span className="pill">USD</span></div><div className="payout-block"><div className="payout-row"><span>Expected payout</span><strong>{run ? money(run.expectedPayout) : '—'}</strong></div><div className="payout-row"><span>Actual settled</span><strong>{run ? money(run.actualSettled) : '—'}</strong></div><div className="payout-track"><div style={{width: run && Number(run.expectedPayout) ? `${Math.min(100, Math.max(0, Number(run.actualSettled)/Number(run.expectedPayout)*100))}%` : '0%'}}/></div><div className="payout-difference"><span>Net difference</span><strong className={Number(run?.payoutDifference) < 0 ? 'negative' : ''}>{run ? money(run.payoutDifference) : '—'}</strong></div></div><div className="info-note"><CircleHelp size={16}/><span>Expected payout uses the published fee schedule. Actual settled sums the valid imported settlement rows.</span></div></section></div>
          <section className="panel"><div className="panel-head"><div><h2>Recent reconciliation</h2><p>Latest run and dataset readiness</p></div><button className="text-button" onClick={() => setPage('Run history')}>View details →</button></div><div className="table-wrap"><table><thead><tr><th>Run ID</th><th>Created</th><th>Internal rows</th><th>Settlement rows</th><th>Quarantined</th><th>Status</th></tr></thead><tbody>{run ? <tr><td className="mono">{run.runId?.slice(0, 8)}…</td><td>{date(run.createdAt)}</td><td>{run.internalCount}</td><td>{run.settlementCount}</td><td>{(run.quarantinedInternal||0)+(run.quarantinedSettlements||0)}</td><td><span className="status complete">Completed</span></td></tr> : <tr><td colSpan="6"><Empty title="No runs recorded" text="Your reconciliation history will appear here."/></td></tr>}</tbody></table></div></section>
        </>}
        {page === 'Import data' && <><div className="page-heading"><div><div className="eyebrow">DATA INGESTION</div><h1>Import datasets</h1><p>Upload ledger transactions and processor settlements before running reconciliation.</p></div></div><div className="import-grid"><UploadCard title="Internal transactions" subtitle="Your payment ledger · CSV" icon={FileSpreadsheet} accept=".csv,text/csv" file={internalFile} setFile={setInternalFile} onUpload={() => upload('internal-transactions', internalFile)} busy={busy} status={imports.internal} errors={imports.internalErrors}/><UploadCard title="Processor settlements" subtitle="Acquirer settlement file · JSON array" icon={FileJson} accept=".json,application/json" file={settlementFile} setFile={setSettlementFile} onUpload={() => upload('settlements', settlementFile)} busy={busy} status={imports.settlements} errors={imports.settlementErrors}/></div><section className="panel run-panel"><div className="run-symbol"><ArrowLeftRight size={22}/></div><div className="run-copy"><h2>Ready to reconcile?</h2><p>Compare transaction records, validate fee deductions, and identify settlement breaks. Results are saved to the local database.</p><div className="readiness"><span className={imports.internal?.accepted !== undefined ? 'ready' : ''}><i/>{imports.internal?.accepted !== undefined ? `${imports.internal.accepted} internal rows loaded` : 'Internal file not imported'}</span><span className={imports.settlements?.accepted !== undefined ? 'ready' : ''}><i/>{imports.settlements?.accepted !== undefined ? `${imports.settlements.accepted} settlement rows loaded` : 'Settlement file not imported'}</span></div></div><button className="button primary" disabled={busy || imports.internal?.accepted === undefined || imports.settlements?.accepted === undefined} onClick={reconcile}>{busy ? 'Processing…' : 'Run reconciliation'} <ArrowLeftRight size={16}/></button></section><section className="panel tips-panel"><h3>Before you upload</h3><ul><li>Internal file should be a CSV with a header row and the columns defined in the assignment README.</li><li>Settlement file should be a JSON array. Currency must be USD and amounts must be numeric.</li><li>Invalid records are quarantined and excluded from financial break counts.</li><li>Re-importing replaces the active input dataset; previous reconciliation runs remain available.</li></ul></section></>}
        {page === 'Break investigation' && <><div className="page-heading"><div><div className="eyebrow">EXCEPTIONS MANAGEMENT</div><h1>Break investigation</h1><p>Inspect unmatched records, amount issues, fee discrepancies, and duplicate settlements.</p></div><button className="button secondary" onClick={() => run && loadBreaks(run.runId, filter)}><RefreshCw size={16}/> Refresh</button></div><section className="panel"><div className="filters"><div className="search-box"><Search size={17}/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search merchant, reference, or reason…"/></div><div className="select-box"><Filter size={16}/><select value={filter} onChange={e=>setFilter(e.target.value)}>{categories.map(c=><option key={c} value={c}>{c==='ALL'?'All categories':shortCategory(c)}</option>)}</select></div><span className="result-count">{visibleBreaks.length} results</span></div><div className="table-wrap"><table><thead><tr><th>Category</th><th>Merchant</th><th>Internal ref</th><th>Network ref</th><th>Expected</th><th>Actual</th><th>Reason</th></tr></thead><tbody>{visibleBreaks.map(r=><tr key={r.id}><td><span className={`category ${r.category?.toLowerCase()}`}>{shortCategory(r.category)}</span></td><td>{r.merchantId || '—'}</td><td className="mono">{r.internal?.merchant_ref || '—'}</td><td className="mono">{r.settlement?.network_ref || '—'}</td><td>{money(r.expectedAmount)}</td><td>{money(r.actualAmount)}</td><td className="reason-cell">{r.reason}</td></tr>)}{!visibleBreaks.length&&<tr><td colSpan="7"><Empty title={run ? 'No breaks found' : 'Run a reconciliation first'} text={run ? 'Try changing the category filter or search term.' : 'Import both datasets and start a reconciliation to see exceptions.'}/></td></tr>}</tbody></table></div></section></>}
        {page === 'Merchants' && <><div className="page-heading"><div><div className="eyebrow">MERCHANT PERFORMANCE</div><h1>Merchant rollup</h1><p>Review clean matches and outstanding exceptions by merchant.</p></div></div><section className="panel"><div className="table-wrap"><table><thead><tr><th>Merchant ID</th><th>Matched records</th><th>Break count</th><th>Absolute break amount</th><th>Health</th></tr></thead><tbody>{merchantRows.map(m=><tr key={m.merchantId}><td><strong>{m.merchantId}</strong></td><td>{m.matched}</td><td>{m.breaks}</td><td>{money(m.breakAmount)}</td><td><span className={`status ${m.breaks ? 'review' : 'complete'}`}>{m.breaks ? 'Review required' : 'Clean'}</span></td></tr>)}{!merchantRows.length&&<tr><td colSpan="5"><Empty title="No merchant report yet" text="Merchant summaries are generated after a reconciliation run."/></td></tr>}</tbody></table></div></section></>}
        {page === 'Run history' && <><div className="page-heading"><div><div className="eyebrow">AUDIT TRAIL</div><h1>Run history</h1><p>Inspect the latest persisted reconciliation and current import status.</p></div><button className="button primary" onClick={reconcile} disabled={busy}>{busy?'Processing…':'Run again'} <RefreshCw size={16}/></button></div><section className="panel"><div className="panel-head"><div><h2>Latest run</h2><p>Persisted in the local H2 database</p></div><span className={`status ${run?'complete':'review'}`}>{run?'Completed':'No run yet'}</span></div>{run?<div className="detail-grid"><Detail label="Run ID" value={run.runId}/><Detail label="Created at" value={date(run.createdAt)}/><Detail label="Internal records" value={run.internalCount}/><Detail label="Settlement records" value={run.settlementCount}/><Detail label="Quarantined internal rows" value={run.quarantinedInternal}/><Detail label="Quarantined settlement rows" value={run.quarantinedSettlements}/></div>:<Empty title="No reconciliation runs" text="Import both datasets and run reconciliation."/>}</section><section className="panel"><div className="panel-head"><div><h2>Import history snapshot</h2><p>Most recent import for each source</p></div></div><div className="detail-grid"><Detail label="Internal file" value={imports.internal?.filename || 'Not imported'}/><Detail label="Imported at" value={date(imports.internal?.at)}/><Detail label="Settlement file" value={imports.settlements?.filename || 'Not imported'}/><Detail label="Imported at" value={date(imports.settlements?.at)}/></div></section></>}
        <footer>Settlewise · Payment settlement reconciliation <span>Built for transparent, auditable payment operations</span></footer>
      </div>
    </main>
  </div>;
}
function Metric({title,value,note,icon:Icon,tone}) { return <div className="metric-card"><div className={`metric-icon ${tone}`}><Icon size={19}/></div><div className="metric-title">{title}</div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></div>; }
function Empty({title,text}) { return <div className="empty-state"><div className="empty-icon"><FileCheck2 size={21}/></div><strong>{title}</strong><span>{text}</span></div>; }
function Detail({label,value}) { return <div className="detail-item"><span>{label}</span><strong>{value ?? '—'}</strong></div>; }
function UploadCard({title,subtitle,icon:Icon,accept,file,setFile,onUpload,busy,status,errors=[]}) { return <section className="panel upload-card"><div className="upload-title"><div className="upload-icon"><Icon size={22}/></div><div><h2>{title}</h2><p>{subtitle}</p></div></div><label className={`dropzone ${file?'selected':''}`}><input type="file" accept={accept} onChange={e=>setFile(e.target.files?.[0]||null)}/><div className="drop-icon"><ArrowDownToLine size={22}/></div><strong>{file ? file.name : 'Choose a file or drag it here'}</strong><span>{file ? `${(file.size/1024).toFixed(1)} KB · Ready to upload` : accept.includes('csv') ? 'CSV file · Max 15 MB' : 'JSON file · Max 15 MB'}</span></label><button className="button secondary full" disabled={busy||!file} onClick={onUpload}><Upload size={16}/>{busy?'Uploading…':'Upload and validate'}</button>{status?.accepted!==undefined&&<div className="upload-status"><BadgeCheck size={16}/><span><strong>{status.accepted} rows accepted</strong><small>{status.quarantined||0} quarantined · {date(status.at)}</small></span></div>}{errors?.length>0&&<div className="quarantine-note"><AlertTriangle size={15}/>{errors.length} malformed rows quarantined</div>}</section>; }
export default App;
