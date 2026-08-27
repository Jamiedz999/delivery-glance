import { apiRequest } from './http'

export type ProofArtifactKind = 'PHOTO' | 'SIGNATURE'

export type ProofStatus = 'PENDING' | 'READY' | 'REJECTED'

/** What the presign endpoint hands back: a one-shot URL, and the key to name on the handoff. */
export interface IssuedUpload {
  uploadUrl: string
  objectKey: string
}

export interface ProofArtifactView {
  kind: ProofArtifactKind
  status: ProofStatus
  capturedAt: string
  processedAt: string | null
  /** Present only for a READY artifact; null while processing or if rejected. */
  thumbnailUrl: string | null
  fullUrl: string | null
}

export interface ProofSet {
  artifacts: ProofArtifactView[]
}

function requestProofUpload(
  deliveryId: string,
  kind: ProofArtifactKind,
  contentType: string,
): Promise<IssuedUpload> {
  return apiRequest<IssuedUpload>(`/api/couriers/me/deliveries/${deliveryId}/proof-uploads`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ kind, contentType }),
  })
}

/**
 * Mint a presigned URL for one captured artifact and upload the bytes straight to it, returning the
 * object key to attach to the handoff command. The PUT goes to S3, not this application, and carries
 * no session cookie or CSRF header — it is a bearer capability to one object, and nothing else.
 */
export async function uploadCapturedArtifact(
  deliveryId: string,
  kind: ProofArtifactKind,
  blob: Blob,
): Promise<string> {
  const { uploadUrl, objectKey } = await requestProofUpload(deliveryId, kind, blob.type)
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    body: blob,
    headers: { 'Content-Type': blob.type },
  })
  if (!response.ok) {
    throw new Error(`Proof upload failed with ${response.status}`)
  }
  return objectKey
}

export function fetchDeliveryProof(deliveryId: string): Promise<ProofSet> {
  return apiRequest<ProofSet>(`/api/deliveries/${deliveryId}/proof`)
}

export const PROOF_KIND_LABELS: Record<ProofArtifactKind, string> = {
  PHOTO: 'Delivery photo',
  SIGNATURE: 'Recipient signature',
}

export const PROOF_STATUS_LABELS: Record<ProofStatus, string> = {
  PENDING: 'Processing…',
  READY: 'Ready',
  REJECTED: 'Rejected',
}
