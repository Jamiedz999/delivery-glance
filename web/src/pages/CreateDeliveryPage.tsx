import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/http'
import { useCreateDelivery } from '../api/queries'

interface AddressFields {
  addressLabel: string
  latitude: string
  longitude: string
}

const EMPTY_ADDRESS: AddressFields = { addressLabel: '', latitude: '', longitude: '' }

export function CreateDeliveryPage() {
  const navigate = useNavigate()
  const create = useCreateDelivery()
  const [reference, setReference] = useState('')
  const [pickup, setPickup] = useState<AddressFields>(EMPTY_ADDRESS)
  const [handoff, setHandoff] = useState<AddressFields>(EMPTY_ADDRESS)

  const fieldErrors = create.error instanceof ApiError ? create.error.fieldMessages() : {}

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    create.mutate(
      {
        reference,
        pickup: toAddress(pickup),
        handoff: toAddress(handoff),
      },
      { onSuccess: (created) => void navigate(`/deliveries/${created.id}`) },
    )
  }

  return (
    <section>
      <h1>New delivery</h1>

      {create.isError && (
        <p role="alert" className="error">
          {summaryFor(create.error)}
        </p>
      )}

      <form onSubmit={submit} noValidate>
        <Field id="reference" label="Delivery reference" error={fieldErrors.reference}>
          <input
            id="reference"
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            aria-invalid={fieldErrors.reference !== undefined}
            aria-describedby={fieldErrors.reference !== undefined ? 'reference-error' : undefined}
          />
        </Field>

        <AddressFieldset
          legend="Pickup address"
          name="pickup"
          value={pickup}
          onChange={setPickup}
          errors={fieldErrors}
        />
        <AddressFieldset
          legend="Handoff address"
          name="handoff"
          value={handoff}
          onChange={setHandoff}
          errors={fieldErrors}
        />

        <button type="submit" disabled={create.isPending} aria-busy={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create delivery'}
        </button>
      </form>
    </section>
  )
}

interface AddressFieldsetProps {
  legend: string
  name: 'pickup' | 'handoff'
  value: AddressFields
  onChange: (value: AddressFields) => void
  errors: Record<string, string>
}

function AddressFieldset({ legend, name, value, onChange, errors }: AddressFieldsetProps) {
  return (
    <fieldset>
      <legend>{legend}</legend>

      <Field id={`${name}-label`} label="Address" error={errors[`${name}.addressLabel`]}>
        <input
          id={`${name}-label`}
          value={value.addressLabel}
          onChange={(event) => onChange({ ...value, addressLabel: event.target.value })}
          aria-invalid={errors[`${name}.addressLabel`] !== undefined}
          aria-describedby={errors[`${name}.addressLabel`] !== undefined ? `${name}-label-error` : undefined}
        />
      </Field>

      <Field id={`${name}-latitude`} label="Latitude" error={errors[`${name}.latitude`]}>
        <input
          id={`${name}-latitude`}
          type="number"
          step="any"
          min={-90}
          max={90}
          value={value.latitude}
          onChange={(event) => onChange({ ...value, latitude: event.target.value })}
          aria-invalid={errors[`${name}.latitude`] !== undefined}
          aria-describedby={errors[`${name}.latitude`] !== undefined ? `${name}-latitude-error` : undefined}
        />
      </Field>

      <Field id={`${name}-longitude`} label="Longitude" error={errors[`${name}.longitude`]}>
        <input
          id={`${name}-longitude`}
          type="number"
          step="any"
          min={-180}
          max={180}
          value={value.longitude}
          onChange={(event) => onChange({ ...value, longitude: event.target.value })}
          aria-invalid={errors[`${name}.longitude`] !== undefined}
          aria-describedby={errors[`${name}.longitude`] !== undefined ? `${name}-longitude-error` : undefined}
        />
      </Field>
    </fieldset>
  )
}

interface FieldProps {
  id: string
  label: string
  error?: string
  children: ReactNode
}

function Field({ id, label, error, children }: FieldProps) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error !== undefined && (
        <p id={`${id}-error`} role="alert" className="error">
          {label} {error}
        </p>
      )}
    </div>
  )
}

function toAddress(fields: AddressFields) {
  return {
    addressLabel: fields.addressLabel,
    latitude: toNumber(fields.latitude),
    longitude: toNumber(fields.longitude),
  }
}

function toNumber(raw: string): number | null {
  const parsed = Number(raw.trim())
  return raw.trim() === '' || Number.isNaN(parsed) ? null : parsed
}

function summaryFor(error: unknown): string {
  if (error instanceof ApiError && error.code === 'delivery-reference-taken') {
    return 'Another delivery already uses that reference. Choose a different one.'
  }
  if (error instanceof ApiError && error.code === 'invalid-request') {
    return 'Some details need correcting before this delivery can be created.'
  }
  if (error instanceof ApiError && error.status === 403) {
    return 'Your account is not allowed to create deliveries.'
  }
  return 'Could not create the delivery. Try again in a moment.'
}
