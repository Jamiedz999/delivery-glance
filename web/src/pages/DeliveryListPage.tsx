import { Link } from 'react-router'
import { useDeliveries } from '../api/queries'
import { DELIVERY_STATE_CHIP_CLASS, DELIVERY_STATE_LABELS } from '../api/deliveries'

export function DeliveryListPage() {
  const { data: deliveries, isPending, isError } = useDeliveries()

  return (
    <section>
      <div className="page-heading">
        <h1>Deliveries</h1>
        <Link className="btn-primary" to="/deliveries/new">
          New delivery
        </Link>
      </div>

      {isPending && <p role="status">Loading deliveries…</p>}
      {isError && <p role="alert">Could not load deliveries. Reload the page to try again.</p>}

      {deliveries?.length === 0 && (
        <div className="card empty-state">
          <p role="status">No deliveries yet. Create the first one to see it here.</p>
        </div>
      )}

      {deliveries != null && deliveries.length > 0 && (
        <ul className="delivery-list">
          {deliveries.map((delivery) => (
            <li key={delivery.id}>
              <article className="card delivery-row">
                <div className="delivery-row-top">
                  <h2 className="delivery-row-ref">
                    <Link to={`/deliveries/${delivery.id}`}>{delivery.reference}</Link>
                  </h2>
                  <span className={`status-chip ${DELIVERY_STATE_CHIP_CLASS[delivery.state]}`}>
                    {DELIVERY_STATE_LABELS[delivery.state]}
                  </span>
                </div>
                <p className="delivery-row-handoff">{delivery.handoffAddressLabel}</p>
                <p className="delivery-row-meta">
                  Created{' '}
                  <time dateTime={delivery.createdAt}>{new Date(delivery.createdAt).toLocaleString()}</time>
                </p>
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
