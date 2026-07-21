import type { MerchantRollup } from '../api/types'
import { formatMoney } from '../lib/format'

interface Props {
  merchants: MerchantRollup[]
}

export function MerchantTable({ merchants }: Props) {
  return (
    <section className="panel">
      <h2>Per-merchant rollup</h2>
      <table>
        <thead>
          <tr>
            <th>Merchant</th>
            <th className="numeric">Items</th>
            <th className="numeric">Breaks</th>
            <th className="numeric">Ledger gross</th>
            <th className="numeric">Settled</th>
          </tr>
        </thead>
        <tbody>
          {merchants.map((merchant) => (
            <tr key={merchant.merchantId}>
              <td>{merchant.merchantId}</td>
              <td className="numeric">{merchant.itemCount}</td>
              <td className={`numeric ${merchant.breakCount > 0 ? 'value-alert' : ''}`}>{merchant.breakCount}</td>
              <td className="numeric">{formatMoney(merchant.totalGross)}</td>
              <td className="numeric">{formatMoney(merchant.totalSettled)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
