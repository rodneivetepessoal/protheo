package com.protheo.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Producer CDI para clients AWS SDK
 */
@ApplicationScoped
public class AwsClientProducer {

    private final Region region;

    public AwsClientProducer() {
        String regionEnv = System.getenv("AWS_REGION");
        this.region = regionEnv != null ? Region.of(regionEnv) : Region.US_EAST_1;
    }

    @Produces
    @ApplicationScoped
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(region)
                .build();
    }

    @Produces
    @ApplicationScoped
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Produces
    @ApplicationScoped
    public S3Client s3Client() {
        return S3Client.builder()
                .region(region)
                .build();
    }

    @Produces
    @ApplicationScoped
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(region)
                .build();
    }

    @Produces
    @ApplicationScoped
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(region)
                .build();
    }
}
