import { Link } from 'react-router'
import { useDeliveries } from '../api/queries'
import { DELIVERY_STATE_LABELS } from '../api/deliveries'

export function DeliveryListPage() {
  const { data: deliveries, isPending, isError } = useDeliveries()

  return (
    <section>
      <div className="page-heading">
        <h1>Deliveries</h1>
        <Link to="/deliveries/new">New delivery</Link>
      </div>

      {isPending && <p role="status">Loading deliveries…</p>}
      {isError && <p role="alert">Could not load deliveries. Reload the page to try again.</p>}

      {deliveries?.length === 0 && (
        <p role="status">No deliveries yet. Create the first one to see it here.</p>
      )}

      {deliveries != null && deliveries.length > 0 && (
        <table>
          <caption className="visually-hidden">Deliveries, newest first</caption>
          <thead>
            <tr>
              <th scope="col">Reference</th>
              <th scope="col">Status</th>
              <th scope="col">Handoff</th>
              <th scope="col">Created</th>
            </tr>
          </thead>
          <tbody>
            {deliveries.map((delivery) => (
              <tr key={delivery.id}>
                <th scope="row">
                  <Link to={`/deliveries/${delivery.id}`}>{delivery.reference}</Link>
                </th>
                <td>{DELIVERY_STATE_LABELS[delivery.state]}</td>
                <td>{delivery.handoffAddressLabel}</td>
                <td>
                  <time dateTime={delivery.createdAt}>{new Date(delivery.createdAt).toLocaleString()}</time>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
