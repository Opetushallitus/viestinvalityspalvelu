package fi.oph.viestinvalitys.aws

import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil, Mode}
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.{ContainerCredentialsProvider, HttpCredentialsProvider, SystemPropertyCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.net.URI

object AwsUtil {

  val mode = ConfigurationUtil.getMode()

  def getCredentialsProvider(): HttpCredentialsProvider =
    ContainerCredentialsProvider.builder().build()

  def getS3Client(): S3Client =
    if(mode==Mode.LOCAL)
      S3Client.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .forcePathStyle(true)
        .build()
    else
      S3Client.builder()
        .credentialsProvider(getCredentialsProvider())
        .build()}
