import * as cdk from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as constructs from "constructs";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as route53_targets from "aws-cdk-lib/aws-route53-targets";
import * as certificatemanager from "aws-cdk-lib/aws-certificatemanager";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as rds from "aws-cdk-lib/aws-rds";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as logs from "aws-cdk-lib/aws-logs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deployment from "aws-cdk-lib/aws-s3-deployment";
import * as cloudfront_origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as sqs from "aws-cdk-lib/aws-sqs";
import * as lambda_event_sources from "aws-cdk-lib/aws-lambda-event-sources";
import * as events from "aws-cdk-lib/aws-events";
import * as events_targets from "aws-cdk-lib/aws-events-targets";
import * as ses from "aws-cdk-lib/aws-ses";
import * as iam from "aws-cdk-lib/aws-iam";
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
    opintopolkuHostedZone: route53.IHostedZone,
    database: rds.DatabaseCluster,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
    monitorointiQueue: sqs.Queue,
    emailIndentity: ses.EmailIdentity,
    sesConfigurationSet: ses.ConfigurationSet,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    const sharedAppLogGroup = this.createSharedAppLogGroup();
    const sharedAuditLogGroup = this.createSharedAuditLogGroup();
    this.createPublicFacingFunctions(
      vpc,
      sharedAppLogGroup,
      sharedAuditLogGroup,
      database,
      databaseAccessSecurityGroup,
      attachmentsBucket,
      hostedZone,
      opintopolkuHostedZone,
    );

    this.createTilanpaivitysFunction(
      database,
      vpc,
      databaseAccessSecurityGroup,
      sharedAppLogGroup,
      monitorointiQueue,
    );

    this.createAjastus(
      database,
      vpc,
      databaseAccessSecurityGroup,
      attachmentsBucket,
      sharedAppLogGroup,
      sharedAuditLogGroup,
      emailIndentity,
      sesConfigurationSet,
    );
  }

  private metricDataNamespace = "viestinvalitys";

  private createAjastus(
    database: rds.DatabaseCluster,
    vpc: ec2.IVpc,
    databaseSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
    sharedAppLogGroup: logs.LogGroup,
    sharedAuditLogGroup: logs.LogGroup,
    emailIndentity: ses.EmailIdentity,
    sesConfigurationSet: ses.ConfigurationSet,
  ) {
    const ajastusQueue = new sqs.Queue(this, "AjastusQueue", {
      queueName: "viestinvalityspalvelu-ajastus",
      visibilityTimeout: cdk.Duration.seconds(60),
      enforceSSL: true,
    });

    /**
     * Lähetyksen ajastus. Lambda joka kerran minuutissa puskee jonoon joukon sqs-viestejä joiden näkyvyys on ajatestettu
     * niin että uusi viesti tulee näkyviin joka n:äs sekunti
     */
    const ajastusName = "ajastus";
    const ajastusFunction = this.createFunction(
      ajastusName,
      {
        AJASTUS_QUEUE_URL: ajastusQueue.queueUrl,
        PING_HOSTNAME: config.getConfig().domainName,
      },
      vpc,
      sharedAppLogGroup,
    );
    ajastusQueue.grantSendMessages(ajastusFunction);

    const alias = this.createAlias(ajastusName, ajastusFunction);
    const rule = new events.Rule(this, "AjastusRule", {
      schedule: events.Schedule.rate(cdk.Duration.minutes(1)),
    });
    rule.addTarget(new events_targets.LambdaFunction(alias));

    const lahetysName = "lahetys";
    const lahetysFunction = this.createFunction(
      lahetysName,
      {
        DB_SECRET_ID: database.secret?.secretName!,
        AJASTUS_QUEUE_URL: ajastusQueue.queueUrl,
        CONFIGURATION_SET_NAME: sesConfigurationSet.configurationSetName,
        MODE: config.getConfig().mode,
        ATTACHMENTS_BUCKET_NAME: attachmentsBucket.bucketName,
        AUDIT_LOG_GROUP_NAME: sharedAuditLogGroup.logGroupName,
        METRIC_DATA_NAMESPACE: this.metricDataNamespace,
        FAKEMAILER_HOST: "localhost",
        FAKEMAILER_PORT: "25",
      },
      vpc,
      sharedAppLogGroup,
      databaseSecurityGroup,
    );
    database.secret?.grantRead(lahetysFunction);
    attachmentsBucket.grantRead(lahetysFunction);
    ajastusQueue.grantConsumeMessages(lahetysFunction);
    emailIndentity.grant(lahetysFunction, "ses:SendRawEmail");
    lahetysFunction.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ["cloudwatch:PutMetricData"],
        resources: ["*"],
      }),
    );

    const lahetysAlias = this.createAlias(lahetysName, lahetysFunction);
    lahetysAlias.addEventSource(
      new lambda_event_sources.SqsEventSource(ajastusQueue),
    );
  }

  private createPublicFacingFunctions(
    vpc: ec2.IVpc,
    sharedAppLogGroup: logs.LogGroup,
    sharedAuditLogGroup: logs.LogGroup,
    database: rds.DatabaseCluster,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    attachmentsBucket: s3.Bucket,
    hostedZone: route53.IHostedZone,
    opintopolkuHostedZone: route53.IHostedZone,
  ) {
    const casSecret = secretsmanager.Secret.fromSecretNameV2(
      this,
      "CasSecret",
      "cas-secret",
    );
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
      opintopolkuHostedZone,
      vastaanottoFunctionUrl,
      raportointiFunctionUrl,
      swaggerUIBucket,
    );

    this.createRaportointiKayttoliittyma(distribution);
  }

  private createTilanpaivitysFunction(
    database: rds.DatabaseCluster,
    vpc: ec2.IVpc,
    databaseAccessSecurityGroup: ec2.SecurityGroup,
    sharedAppLogGroup: logs.LogGroup,
    monitorointiQueue: sqs.Queue,
  ) {
    const functionName = "tilapaivitys";
    const lambdaFunction = this.createFunction(
      functionName,
      {
        DB_SECRET_ID: database.secret?.secretName!,
        SES_MONITOROINTI_QUEUE_URL: monitorointiQueue.queueUrl,
      },
      vpc,
      sharedAppLogGroup,
      databaseAccessSecurityGroup,
    );
    monitorointiQueue.grantSendMessages(lambdaFunction);
    database.secret?.grantRead(lambdaFunction);

    const alias = this.createAlias(functionName, lambdaFunction);
    alias.addEventSource(
      new lambda_event_sources.SqsEventSource(monitorointiQueue),
    );
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
    const environment = {
      OPINTOPOLKU_DOMAIN: config.getConfig().opintopolkuDomainName,
      VIESTINVALITYS_URL: `https://${config.getConfig().domainName}`,
      DB_SECRET_ID: database.secret?.secretName!,
      CAS_SECRET_ID: casSecret.secretName,
      AUDIT_LOG_GROUP_NAME: auditLogGroup.logGroupName,
      METRIC_DATA_NAMESPACE: this.metricDataNamespace,
      ATTACHMENTS_BUCKET_NAME: attachmentsBucket.bucketName,
      MODE: config.getConfig().mode,
    };
    const lambdaFunction = this.createFunction(
      functionName,
      environment,
      vpc,
      appLogGroup,
      databaseAccessSecurityGroup,
    );

    database.secret?.grantRead(lambdaFunction);
    casSecret.grantRead(lambdaFunction);
    attachmentsBucket.grantWrite(lambdaFunction);
    lambdaFunction.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ["cloudwatch:PutMetricData"],
        resources: ["*"],
      }),
    );

    const alias = this.createAlias(functionName, lambdaFunction);
    return alias.addFunctionUrl({
      authType: lambda.FunctionUrlAuthType.NONE,
    });
  }

  private createAlias(functionName: string, lambdaFunction: lambda.Function) {
    return new lambda.Alias(
      this,
      `${this.capitalize(functionName)}LambdaAlias`,
      {
        aliasName: "Current",
        version: lambdaFunction.currentVersion,
      },
    );
  }

  private createFunction(
    functionName: string,
    environment: { [p: string]: string },
    vpc: ec2.IVpc,
    appLogGroup: logs.LogGroup,
    securityGroup?: ec2.SecurityGroup,
  ) {
    const capitalizedFunctionName = this.capitalize(functionName);
    return new lambda.Function(this, `${capitalizedFunctionName}Lambda`, {
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
      environment: environment,
      vpc: vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      securityGroups: securityGroup ? [securityGroup] : [],
      loggingFormat: lambda.LoggingFormat.JSON,
      applicationLogLevelV2: lambda.ApplicationLogLevel.INFO,
      logGroup: appLogGroup,
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS,
    });
  }

  private capitalize(s: string) {
    return `${s.charAt(0).toUpperCase()}${s.slice(1)}`;
  }

  private createCloudfrontDistribution(
    hostedZone: route53.IHostedZone,
    opintopolkuHostedZone: route53.IHostedZone,
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

    const exisitngDistribution = cloudfront.Distribution.fromDistributionAttributes(
      this,
      "OpintopolunViestinvalitysDistribution", {
        domainName: config.getConfig().opintopolkuCloudFront.domainName,
        distributionId: config.getConfig().opintopolkuCloudFront.distributionId,
      }
    );
    new route53.ARecord(this, "SiteAliasRecord", {
      zone: opintopolkuHostedZone,
      recordName: `viestinvalitys.${config.getConfig().opintopolkuDomainName}`,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.CloudFrontTarget(exisitngDistribution)
      ),
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
