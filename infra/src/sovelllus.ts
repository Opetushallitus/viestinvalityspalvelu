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
import * as s3deployment from "aws-cdk-lib/aws-s3-deployment";
import * as cloudfront_origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as path from "node:path";
import * as config from "./config";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import * as nextjs_standaldone from "cdk-nextjs-standalone";

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
    const raportointiFunctionUrl = this.createRaportointiFunctionUrl(
      vpc,
      casSecret,
      sharedAppLogGroup,
      sharedAuditLogGroup,
      database,
      databaseAccessSecurityGroup,
      attachmentsBucket,
    );
    const swaggerUIBucket = this.createSwaggerUIBucket();

    const distribution = this.createCloudfrontDistribution(
      hostedZone,
      vastaanottoFunctionUrl,
      raportointiFunctionUrl,
      swaggerUIBucket,
    );

    this.createRaportointiKayttoliittyma(distribution);
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
    return this.createFunctionUrl(
      vpc,
      casSecret,
      appLogGroup,
      auditLogGroup,
      database,
      databaseAccessSecurityGroup,
      attachmentsBucket,
      "vastaanotto",
    );
  }

  private createRaportointiFunctionUrl(
    vpc: ec2.IVpc,
    casSecret: secretsmanager.ISecret,
    appLogGroup: logs.LogGroup,
    auditLogGroup: logs.LogGroup,
    database: rds.DatabaseCluster,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
  ) {
    return this.createFunctionUrl(
      vpc,
      casSecret,
      appLogGroup,
      auditLogGroup,
      database,
      databaseAccessSecurityGroup,
      attachmentsBucket,
      "raportointi",
    );
  }

  private createFunctionUrl(
    vpc: ec2.IVpc,
    casSecret: secretsmanager.ISecret,
    appLogGroup: logs.LogGroup,
    auditLogGroup: logs.LogGroup,
    database: rds.DatabaseCluster,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
    functionName: string,
  ) {
    const capitalizedFunctionName = `${functionName.charAt(0).toUpperCase()}${functionName.slice(1)}`;
    const lambdaFunction = new lambda.Function(
      this,
      `${capitalizedFunctionName}Lambda`,
      {
        functionName: `viestinvalityspalvelu-${functionName}`,
        runtime: lambda.Runtime.JAVA_21,
        handler: `fi.oph.viestinvalitys.${functionName}.LambdaHandler`,
        code: lambda.Code.fromAsset(
          path.join(
            __dirname,
            `../../lambdat/${functionName}/target/${functionName}.zip`,
          ),
        ),
        timeout: cdk.Duration.seconds(60),
        memorySize: 1536,
        architecture: lambda.Architecture.ARM_64,
        environment: {
          OPINTOPOLKU_DOMAIN: config.getConfig().opintopolkuDomainName,
          VIESTINVALITYS_URL: `https://${config.getConfig().domainName}`,
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
      },
    );

    database.secret?.grantRead(lambdaFunction);
    casSecret.grantRead(lambdaFunction);
    attachmentsBucket.grantWrite(lambdaFunction);

    const alias = new lambda.Alias(
      this,
      `${capitalizedFunctionName}LambdaAlias`,
      {
        aliasName: "Current",
        version: lambdaFunction.currentVersion,
      },
    );

    return alias.addFunctionUrl({
      authType: lambda.FunctionUrlAuthType.NONE,
    });
  }

  private createCloudfrontDistribution(
    hostedZone: route53.IHostedZone,
    vastaanottoFunctionUrl: lambda.FunctionUrl,
    raportointiFunctionUrl: lambda.FunctionUrl,
    swaggerUIBucket: s3.Bucket,
  ) {
    const domainName = config.getConfig().domainName;
    const certificate = this.createDnsValidatedCertificate(
      domainName,
      hostedZone,
    );
    const noCachePolicy = this.createNoCachePolicy();
    const originRequestPolicy = this.createOriginRequestPolicy();
    const vastaanottoFunctionUrlOrigin =
      new cloudfront_origins.FunctionUrlOrigin(vastaanottoFunctionUrl);
    const raportointiFunctionUrlOrigin =
      new cloudfront_origins.FunctionUrlOrigin(raportointiFunctionUrl);
    const lambdaHeaderFunction = this.createLambdaHeaderFunction();
    const cloudfrontOAI = new cloudfront.OriginAccessIdentity(
      this,
      "CloudfrontOriginAccessIdentity",
    );
    swaggerUIBucket.grantRead(cloudfrontOAI);

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
        "/raportointi/login": {
          origin: raportointiFunctionUrlOrigin,
          cachePolicy: noCachePolicy,
          viewerProtocolPolicy:
            cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          originRequestPolicy,
          responseHeadersPolicy:
            cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        },
        "/raportointi/login/*": {
          origin: raportointiFunctionUrlOrigin,
          cachePolicy: noCachePolicy,
          viewerProtocolPolicy:
            cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          originRequestPolicy,
          responseHeadersPolicy:
            cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        },
        "/raportointi/v1/*": {
          origin: raportointiFunctionUrlOrigin,
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
        "/static/*": {
          origin: new cloudfront_origins.S3Origin(swaggerUIBucket, {
            originAccessIdentity: cloudfrontOAI,
          }),
          cachePolicy: noCachePolicy,
          viewerProtocolPolicy:
            cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          responseHeadersPolicy:
            cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        },
      },
    });

    const subdomain = domainName.split(".")[0];
    new route53.CnameRecord(this, "CloudfrontCnameRecord", {
      zone: hostedZone,
      recordName: subdomain,
      domainName: distribution.domainName,
    });

    return distribution;
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

  private createSwaggerUIBucket() {
    const bucket = new s3.Bucket(this, "SwaggerBucket");

    new s3deployment.BucketDeployment(this, "SwaggerBucketDeployment", {
      sources: [s3deployment.Source.asset("../static")],
      destinationBucket: bucket,
      destinationKeyPrefix: "static",
    });

    return bucket;
  }

  private createRaportointiKayttoliittyma(
    distribution: cloudfront.Distribution,
  ) {
    const domainName = config.getConfig().domainName;

    new nextjs_standaldone.Nextjs(
      this,
      "ViestinvalitysRaportointiNextJsStandalone",
      {
        nextjsPath: "../viestinvalitys-raportointi",
        buildCommand: "../scripts/build-raportointi.sh",
        basePath: "/raportointi",
        distribution: distribution,
        environment: {
          VIRKAILIJA_URL: `https://virkailija.${config.getConfig().opintopolkuDomainName}`,
          VIESTINTAPALVELU_URL: `https://${domainName}`,
          LOGIN_URL: `https://${domainName}/raportointi/login`,
          PORT: "8080",
          COOKIE_NAME: "JSESSIONID",
        },
        overrides: {
          nextjsServer: {
            functionProps: {
              timeout: cdk.Duration.seconds(60),
              logGroup: new logs.LogGroup(
                this,
                "Viestinvalitys raportointikäyttöliittymä NextJs Server",
                {
                  logGroupName: `/aws/lambda/viestinvalitys-raportointikayttoliittyma-nextjs-server`,
                },
              ),
            },
          },
        },
      },
    );
  }
}
