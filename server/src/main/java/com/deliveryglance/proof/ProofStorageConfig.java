package com.deliveryglance.proof;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the proof module's deployment inputs and the one S3 collaborator the application holds:
 * a presigner.
 *
 * <p>The server never streams a proof byte. It signs a URL and hands it back, so the only S3 object
 * it needs is {@link S3Presigner}, which does its work — HMAC over the request — without a network
 * call or an HTTP client. The bytes travel between the browser and S3 directly, and between S3 and
 * the Lambda directly; this configuration is what keeps them off the request thread.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProofProperties.class)
class ProofStorageConfig {

	/**
	 * A presigner aimed at either real S3 or a LocalStack endpoint, decided entirely by
	 * configuration. Constructing one makes no network call, so it is safe to build even in a
	 * deployment that has not finished wiring its bucket; the presign endpoint is where an
	 * unconfigured bucket is actually refused.
	 */
	@Bean
	S3Presigner proofS3Presigner(ProofProperties properties) {
		S3Presigner.Builder builder = S3Presigner.builder()
			.region(Region.of(properties.region()))
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(properties.pathStyleAccess())
				.build());
		if (properties.hasStaticCredentials()) {
			builder.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())));
		}
		if (properties.hasEndpointOverride()) {
			builder.endpointOverride(URI.create(properties.endpointOverride()));
		}
		return builder.build();
	}

}
