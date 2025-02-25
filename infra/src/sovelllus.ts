import * as cdk from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as constructs from "constructs";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as certificatemanager from "aws-cdk-lib/aws-certificatemanager";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as rds from "aws-cdk-lib/aws-rds";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as logs from "aws-cdk-lib/aws-logs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as cloudfront_origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as path from "node:path";
import * as config from "./config";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";

export class SovellusStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    hostedZone: route53.IHostedZone,
    database: rds.DatabaseCluster,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    const casSecret = secretsmanager.Secret.fromSecretNameV2(
      this,
      "CasSecret",
      "cas-secret",
    );
    const sharedAppLogGroup = this.createSharedAppLogGroup();
    const sharedAuditLogGroup = this.createSharedAuditLogGroup();
    const vastaanottoFunctionUrl = this.createVastaanottoFunctionUrl(
      vpc,
      casSecret,
      sharedAppLogGroup,
      sharedAuditLogGroup,
      database,
      databaseAccessSecurityGroup,
      attachmentsBucket,
    );

    this.createCloudfrontDistribution(hostedZone, vastaanottoFunctionUrl);
  }

  private createSharedAuditLogGroup() {
    return new logs.LogGroup(this, "auditLogGroup", {
      logGroupName: "audit-viestinvalityspalvelu",
      retention: logs.RetentionDays.TEN_YEARS,
    });
  }

  private createSharedAppLogGroup() {
    return new logs.LogGroup(this, "LambdaAppLogGroup", {
      logGroupName: "app-viestinvalityspalvelu",
      retention: logs.RetentionDays.TWO_YEARS,
    });
  }

  private createVastaanottoFunctionUrl(
    vpc: ec2.IVpc,
    casSecret: secretsmanager.ISecret,
    appLogGroup: logs.LogGroup,
    auditLogGroup: logs.LogGroup,
    database: rds.DatabaseCluster,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
  ) {
    const vastaanottoLambda = new lambda.Function(this, "VastaanottoLambda", {
      functionName: "viestinvalityspalvelu-vastaanotto",
      runtime: lambda.Runtime.JAVA_21,
      handler: "fi.oph.viestinvalitys.vastaanotto.LambdaHandler",
      code: lambda.Code.fromAsset(
        path.join(
          __dirname,
          "../../lambdat/vastaanotto/target/vastaanotto.zip",
        ),
      ),
      timeout: cdk.Duration.seconds(60),
      memorySize: 1536,
      architecture: lambda.Architecture.ARM_64,
      environment: {
        OPINTOPOLKU_DOMAIN: config.getConfig().opintopolkuDomainName,
        DB_SECRET_ID: database.secret?.secretName!,
        CAS_SECRET_ID: casSecret.secretName,
        AUDIT_LOG_GROUP_NAME: auditLogGroup.logGroupName,
        ATTACHMENTS_BUCKET_NAME: attachmentsBucket.bucketName,
        MODE: config.getConfig().mode,
      },
      vpc: vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      securityGroups: [databaseAccessSecurityGroup],
      loggingFormat: lambda.LoggingFormat.JSON,
      applicationLogLevelV2: lambda.ApplicationLogLevel.INFO,
      logGroup: appLogGroup,
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS,
    });

    database.secret?.grantRead(vastaanottoLambda);
    casSecret.grantRead(vastaanottoLambda);
    attachmentsBucket.grantWrite(vastaanottoLambda);

    const alias = new lambda.Alias(this, "VastaanottoLambdaAlias", {
      aliasName: "Current",
      version: vastaanottoLambda.currentVersion,
    });

    return alias.addFunctionUrl({
      authType: lambda.FunctionUrlAuthType.NONE,
    });
  }

  private createCloudfrontDistribution(
    hostedZone: route53.IHostedZone,
    vastaanottoFunctionUrl: lambda.FunctionUrl,
  ) {
    const subdomain = "viestinvalitys";
    const domainName = `${subdomain}.${config.getConfig().domainName}`;
    const certificate = this.createDnsValidatedCertificate(
      domainName,
      hostedZone,
    );
    const noCachePolicy = this.createNoCachePolicy();
    const originRequestPolicy = this.createOriginRequestPolicy();
    const vastaanottoFunctionUrlOrigin =
      new cloudfront_origins.FunctionUrlOrigin(vastaanottoFunctionUrl);
    const lambdaHeaderFunction = this.createLambdaHeaderFunction();

    const distribution = new cloudfront.Distribution(this, "Distribution", {
      certificate: certificate,
      domainNames: [domainName],
      defaultRootObject: "index.html",
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
      defaultBehavior: {
        origin: vastaanottoFunctionUrlOrigin,
        cachePolicy: noCachePolicy,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        responseHeadersPolicy:
          cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        originRequestPolicy,
      },
      additionalBehaviors: {
        "/lahetys/v1/liitteet": {
          origin: vastaanottoFunctionUrlOrigin,
          cachePolicy: noCachePolicy,
          viewerProtocolPolicy:
            cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          originRequestPolicy,
          functionAssociations: [
            {
              function: lambdaHeaderFunction,
              eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
            },
          ],
          responseHeadersPolicy:
            cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        },
      },
    });
    new route53.CnameRecord(this, "CloudfrontCnameRecord", {
      zone: hostedZone,
      recordName: subdomain,
      domainName: distribution.domainName,
    });
  }

  private createOriginRequestPolicy() {
    return new cloudfront.OriginRequestPolicy(
      this,
      "LambdaOriginRequestPolicy",
      {
        originRequestPolicyName: `originRequestPolicy-viestinvalitys`,
        cookieBehavior: cloudfront.OriginRequestCookieBehavior.all(),
        queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
        headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList(
          "Accept",
          "Content-Type",
          "Content-Type-Original",
        ), // host header must be excluded???
      },
    );
  }

  private createNoCachePolicy() {
    return new cloudfront.CachePolicy(this, `noCachePolicy-viestinvalitys`, {
      cachePolicyName: `noCachePolicy-viestinvalitys`,
      defaultTtl: cdk.Duration.minutes(0),
      minTtl: cdk.Duration.minutes(0),
      maxTtl: cdk.Duration.minutes(0),
    });
  }

  private createDnsValidatedCertificate(
    domainName: string,
    hostedZone: route53.IHostedZone,
  ) {
    return new certificatemanager.DnsValidatedCertificate(
      this,
      "SiteCertificate",
      {
        domainName: domainName,
        hostedZone: hostedZone,
        region: "us-east-1", // Cloudfront only checks this region for certificates.
      },
    );
  }

  private createLambdaHeaderFunction() {
    return new cloudfront.Function(this, "Function", {
      functionName: `viestinvalitys-content-type-original-header`,
      code: cloudfront.FunctionCode.fromInline(
        "function handler(event) {\n" +
          "    var request = event.request;\n" +
          "    var contentType = request.headers['content-type'];\n" +
          "\n" +
          "    if(contentType && (contentType.value.startsWith('multipart/form-data') || contentType.value.startsWith('application/x-www-form-urlencoded'))) {\n" +
          "        request.headers['content-type'] = {value: 'application/octet-stream'};\n" +
          "        request.headers['content-type-original'] = contentType;\n" +
          "    }    \n" +
          "\n" +
          "    return request;\n" +
          "}",
      ),
    });
  }
}
