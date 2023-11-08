package fi.oph.viestinvalitys.aws

import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.{ContainerCredentialsProvider, HttpCredentialsProvider}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

object AwsUtil {

  def getCredentialsProvider(): HttpCredentialsProvider =
    ContainerCredentialsProvider.builder().build()

  def getS3Client(): S3Client =
    S3Client.builder()
      .credentialsProvider(getCredentialsProvider())
      .build()}
