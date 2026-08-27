package com.deliveryglance.proof;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment inputs for proof of delivery. Issue 50 is the first feature that stores binary
 * artifacts, so every value that makes S3 reachable — and nothing about the bytes themselves —
 * lives here.
 *
 * @param bucket the private bucket proof objects are written to and read from. Required for the
 * feature to work; when blank the presign endpoint refuses rather than minting a URL to nowhere.
 * @param region the bucket's region.
 * @param endpointOverride an S3 endpoint to sign against instead of the real AWS one. Blank in
 * production, where the default virtual-hosted AWS endpoint is correct; set to a LocalStack address
 * for the Compose demo and tests, which is the only supported way this feature runs without an AWS
 * account.
 * @param pathStyleAccess whether presigned URLs address the bucket as a path segment rather than a
 * host prefix. Real S3 uses virtual-hosted style; LocalStack needs path style, so it pairs with an
 * endpoint override.
 * @param accessKeyId static credentials for signing. Blank in production, where the default
 * credentials provider chain (an instance role) is used; set for LocalStack, which accepts any
 * value.
 * @param secretAccessKey the secret half of {@code accessKeyId}.
 * @param presignExpiry how long an issued upload or view URL stays valid. Short by design: a
 * presigned URL is a bearer capability to one object, so it outlives the capture it was minted for
 * by as little as possible.
 * @param maxUploadBytes the largest object the presigned upload will accept. Enforced again in the
 * Lambda, because a presigned PUT cannot cap its own body; this is the size the browser is told and
 * the size the content-length condition signs.
 * @param allowedUploadContentTypes the content types a Courier may request an upload URL for. A
 * belt-and-braces gate in front of the Lambda's own image check, kept here so an obviously wrong
 * request is refused before any URL is minted.
 * @param callbackToken the shared secret the processing Lambda presents to the callback endpoint.
 * Blank disables the callback route entirely, so a deployment without the Lambda cannot have proof
 * metadata written by anything but the Lambda it never configured.
 */
@ConfigurationProperties("delivery-glance.proof")
record ProofProperties(String bucket, String region, String endpointOverride, boolean pathStyleAccess,
		String accessKeyId, String secretAccessKey, Duration presignExpiry, long maxUploadBytes,
		List<String> allowedUploadContentTypes, String callbackToken) {

	ProofProperties {
		region = (region == null || region.isBlank()) ? "us-east-1" : region;
		presignExpiry = (presignExpiry == null) ? Duration.ofMinutes(5) : presignExpiry;
		maxUploadBytes = (maxUploadBytes <= 0) ? 10L * 1024 * 1024 : maxUploadBytes;
		allowedUploadContentTypes = (allowedUploadContentTypes == null || allowedUploadContentTypes.isEmpty())
				? List.of("image/jpeg", "image/png", "image/webp") : List.copyOf(allowedUploadContentTypes);
	}

	boolean isConfigured() {
		return this.bucket != null && !this.bucket.isBlank();
	}

	boolean hasStaticCredentials() {
		return this.accessKeyId != null && !this.accessKeyId.isBlank() && this.secretAccessKey != null
				&& !this.secretAccessKey.isBlank();
	}

	boolean hasEndpointOverride() {
		return this.endpointOverride != null && !this.endpointOverride.isBlank();
	}

	boolean allowsContentType(String contentType) {
		return contentType != null && this.allowedUploadContentTypes.contains(contentType);
	}

}
