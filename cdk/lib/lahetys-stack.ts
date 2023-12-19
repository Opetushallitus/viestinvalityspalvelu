import * as cdk from 'aws-cdk-lib';
import {aws_events, Duration, Fn} from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import {FunctionUrlAuthType} from 'aws-cdk-lib/aws-lambda';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect} from 'aws-cdk-lib/aws-iam';
import {Construct} from 'constructs';
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as acm from "aws-cdk-lib/aws-certificatemanager";
import * as cloudfront_origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as route53targets from "aws-cdk-lib/aws-route53-targets";
import * as targets from 'aws-cdk-lib/aws-events-targets'
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as eventsources from 'aws-cdk-lib/aws-lambda-event-sources';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sns_subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import path = require("path");

interface ViestinValitysStackProps extends cdk.StackProps {
  environmentName: string;
}

export class LahetysStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ViestinValitysStackProps) {
    super(scope, id, props);

    const publicHostedZones: {[p: string]: string} = {
      hahtuva: 'hahtuvaopintopolku.fi'
    }

    const publicHostedZoneIds: {[p: string]: string} = {
      hahtuva: 'Z20VS6J64SGAG9'
    }

    const vpc = ec2.Vpc.fromVpcAttributes(this, "VPC", {
      vpcId: cdk.Fn.importValue(`${props.environmentName}-Vpc`),
      availabilityZones: [
        cdk.Fn.importValue(`${props.environmentName}-SubnetAvailabilityZones`),
      ],
      privateSubnetIds: [
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet1`),
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet2`),
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet3`),
      ],
    });

    const fakemailerHosts: {[p: string]: string} = {
      hahtuva: 'fakemailer-1.fakemailer.hahtuvaopintopolku.fi'
    }

    const fakemailerSecurityGroups: {[p: string]: string} = {
      hahtuva: 'sg-a9f720d3'
    }

    /**
     * Redis-klusteri. Tätä käytetään sessioiden tallentamiseen
     */
    const redisSecurityGroup = new ec2.SecurityGroup(this, "RedisSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-redis`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, "RedisSubnetGroup", {
      cacheSubnetGroupName: `${props.environmentName}-viestinvalityspalvelu`,
      subnetIds: vpc.privateSubnets.map(subnet => subnet.subnetId),
      description: "subnet group for redis"
    })

    const redisCluster = new elasticache.CfnCacheCluster(this, "RedisCluster", {
      clusterName: `${props.environmentName}-viestinvalityspalvelu`,
      engine: "redis",
      cacheNodeType: "cache.t4g.micro",
      numCacheNodes: 1,
      cacheSubnetGroupName: redisSubnetGroup.ref,
      vpcSecurityGroupIds: [redisSecurityGroup.securityGroupId]
    })

    /**
     * SQS-jono lähetyksen ajastamiseen
     */
    const ajastusQueue = new sqs.Queue(this, 'AjastusQueue', {
      queueName: `${props.environmentName}-viestinvalityspalvelu-ajastus`,
      visibilityTimeout: Duration.seconds(60)
    });

    const ajastusSqsAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'sqs:*',
        ],
        resources: [ajastusQueue.queueArn],
      })]
    })

    /**
     * SQS-jono BucketAV-skannerilta tulevien notifikaatioiden prosessointiin
     */
    const skannausTopic = sns.Topic.fromTopicArn(this, 'BucketAVTopic', 'arn:aws:sns:eu-west-1:153563371259:bucketav-stack-FindingsTopic-hKCyHnK3Jkyw')
    const skannausQueue = new sqs.Queue(this, 'SkannausQueue', {
      queueName: `${props.environmentName}-viestinvalityspalvelu-skannaus`,
      visibilityTimeout: Duration.seconds(60)
    });
    skannausTopic.addSubscription(new sns_subscriptions.SqsSubscription(skannausQueue))

    const skannausSqsAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'sqs:*',
        ],
        resources: [skannausQueue.queueArn],
      })]
    })

    /**
     * SQS-jono SES:ltä tulevien notifikaatioiden prosessointiin
     */
    const monitorointiTopic = new sns.Topic(this, `${props.environmentName}-viestinvalityspalvelu-ses-monitorointi`);
    const monitorointiQueue = new sqs.Queue(this, 'MonitorointiQueue', {
      queueName: `${props.environmentName}-viestinvalityspalvelu-ses-monitorointi`,
      visibilityTimeout: Duration.seconds(60)
    });
    monitorointiTopic.addSubscription(new sns_subscriptions.SqsSubscription(monitorointiQueue))

    const monitorointiSqsAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'sqs:*',
        ],
        resources: [monitorointiQueue.queueArn],
      })]
    })

    /**
     * S3 bucket liitetiedostojen tallentamiseen
     */
    const attachmentBucketArn = cdk.Fn.importValue(`${props.environmentName}-viestinvalityspalvelu-liitetiedosto-s3-arn`);

    const attachmentS3Access = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          's3:*', // TODO: määrittele vain tarvittavat oikat
        ],
        resources: [attachmentBucketArn + '/*'],
      })]
    })

    /**
     * Policyt lambdojen oikeuksia varten
     */
    const ssmAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'ssm:GetParameter',
        ],
        resources: [`*`],
      })
      ],
    })

    const sesAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'ses:SendRawEmail',
        ],
        resources: [`*`],
      })
      ],
    })

    const cloudwatchAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'cloudwatch:*', // TODO: vain tarvittavat oikeudet
        ],
        resources: [`*`],
      })
      ],
    })

    /**
     * Security-groupit lambdojen oikeuksia varten
     */
    const postgresSecurityGroupId = cdk.Fn.importValue(`${props.environmentName}-viestinvalityspalvelu-postgres-securitygroupid`);
    const postgresSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "PostgresSecurityGrouop", postgresSecurityGroupId);
    const postgresAccessSecurityGroup = new ec2.SecurityGroup(this, `LambdaPostgresAccessSecurityGroup`,{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-postgresaccess`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(postgresAccessSecurityGroup, ec2.Port.tcp(5432), `Sallitaan postgres access lambdoille`)

    const redisAccessSecurityGroup = new ec2.SecurityGroup(this, `LambdaRedisAccessSecurityGroup`,{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-redisaccess`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    redisSecurityGroup.addIngressRule(redisAccessSecurityGroup, ec2.Port.tcp(6379), "Sallitaan redis access lambdoille")

    const albSecurityGroupId = cdk.Fn.importValue(`${props.environmentName}-EcsAlbSG`);
    const albSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "AlbSecurityGroup", albSecurityGroupId)
    const albAccessSecurityGroup = new ec2.SecurityGroup(this, `LambdaALBAccessSecurityGroup`,{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-albaccess`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    albSecurityGroup.addIngressRule(albAccessSecurityGroup, ec2.Port.tcp(80), "Sallitaan alb access lambdoille")

    const fakemailerSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "FakemailerSecurityGroup", fakemailerSecurityGroups[props.environmentName])
    const fakemailerAccessSecurityGroup = new ec2.SecurityGroup(this, `FakemailerAccessSecurityGroup`,{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-fakemaileraccess`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    fakemailerSecurityGroup.addIngressRule(fakemailerAccessSecurityGroup, ec2.Port.tcp(1025), "Sallitaan fakemailer access lambdoille")

    function getRole(scope: Construct, id: string, inlinePolicies: {[p: string]: cdk.aws_iam.PolicyDocument}) {
      const role = new iam.Role(scope, id, {
        assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
        inlinePolicies,
      });
      role.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
      role.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));
      return role
    }

    function getLambdaAsAlias(scope: Construct, name: string, inVpc: boolean, handler: string, jarPath: string, inlinePolicies: {[p: string]: cdk.aws_iam.PolicyDocument},
                       environment: {[p: string]: string} | undefined, securityGroups:  cdk.aws_ec2.ISecurityGroup[] | undefined) {
      const lambdaFunction = new lambda.Function(scope, `${name}Lambda`, {
        functionName: `${props.environmentName}-viestinvalityspalvelu-${name.toLowerCase()}`,
        runtime: lambda.Runtime.JAVA_17,
        handler,
        code: lambda.Code.fromAsset(path.join(__dirname, `../../${jarPath}`)),
        timeout: Duration.seconds(60),
        memorySize: 1024,
        architecture: lambda.Architecture.X86_64,
        role: getRole(scope, `${name}LambdaRole`, inlinePolicies),
        environment: environment,
        vpc: inVpc ? vpc : undefined,
        securityGroups: securityGroups
      })

      // SnapStart
      const version = lambdaFunction.currentVersion;
      const alias = new lambda.Alias(scope, `${name}LambdaAlias`, {
        aliasName: 'Current',
        version,
      });

      const cfnFunction = lambdaFunction.node.defaultChild as lambda.CfnFunction;
      cfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

      return alias
    }

    /**
     * Lähetyspyyntöjen vastaanotto. Tämä koostuu seuraavista osista:
     *  - CloudFront-distribuutio
     *  - Vastaanottolambda
     *  - Staattinen site Swaggerille
     */
    const vastaanottoAlias = getLambdaAsAlias(this,
        'Vastaanotto',
        true,
        `fi.oph.viestinvalitys.vastaanotto.LambdaHandler`,
        'vastaanotto/target/vastaanotto.jar',
        {
          attachmentS3Access,
          ssmAccess,
          cloudwatchAccess,
        }, {
          "spring_redis_host": redisCluster.attrRedisEndpointAddress,
          "spring_redis_port": `${redisCluster.attrRedisEndpointPort}`,
          "attachment_bucket_arn": attachmentBucketArn,
          "ATTACHMENTS_BUCKET_NAME": `${props.environmentName}-viestinvalityspalvelu-attachments`,
          MODE: 'TEST',
        }, [
          postgresAccessSecurityGroup,
          redisAccessSecurityGroup,
          albAccessSecurityGroup // cas-tickettien validointia varten
        ])

    const vastaanottoFunctionUrl = vastaanottoAlias.addFunctionUrl({
      authType: FunctionUrlAuthType.NONE,
    });

    // staattinen saitti
    const staticBucket = new s3.Bucket(this, 'StaticFiles', {
      bucketName: `${props.environmentName}-viestinvalityspalvelu-static`,
      publicReadAccess: false,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // toistaiseksi ei tarvitse jättää
      autoDeleteObjects: true
    });

    const staticS3Deployment = new s3deploy.BucketDeployment(this, 'DeployWebsite', {
      sources: [s3deploy.Source.asset('../static')],
      destinationBucket: staticBucket,
    });

    const cloudfrontOAI = new cloudfront.OriginAccessIdentity(
        this,
        "cloudfront-OAI",
        {
          comment: `OAI for viestinvälityspalvelu`,
        }
    );
    staticBucket.grantRead(cloudfrontOAI);

    const zone = route53.HostedZone.fromHostedZoneAttributes(
        this,
        "PublicHostedZone",
        {
          zoneName: `${publicHostedZones[props.environmentName]}.`,
          hostedZoneId: `${publicHostedZoneIds[props.environmentName]}`,
        }
    );

    const certificate = new acm.DnsValidatedCertificate(
        this,
        "SiteCertificate",
        {
          domainName: `viestinvalitys.${publicHostedZones[props.environmentName]}`,
          hostedZone: zone,
          region: "us-east-1", // Cloudfront only checks this region for certificates.
        }
    );

    const noCachePolicy = new cloudfront.CachePolicy(
        this,
        `noCachePolicy-${props.environmentName}-viestinvalitys`,
        {
          cachePolicyName: `noCachePolicy-${props.environmentName}-viestinvalitys`,
          defaultTtl: cdk.Duration.minutes(0),
          minTtl: cdk.Duration.minutes(0),
          maxTtl: cdk.Duration.minutes(0),
        }
    );

    const lambdaHeaderFunction = new cloudfront.Function(this, 'Function', {
      functionName: `viestinvalitys-${props.environmentName}-content-type-original-header`,
      code: cloudfront.FunctionCode.fromInline('function handler(event) {\n' +
          '    var request = event.request;\n' +
          '    var contentType = request.headers[\'content-type\'];\n' +
          '\n' +
          '    if(contentType && contentType.value.startsWith(\'multipart/form-data\')) {\n' +
          '        request.headers[\'content-type\'] = {value: \'application/octet-stream\'};\n' +
          '        request.headers[\'content-type-original\'] = contentType;\n' +
          '    }    \n' +
          '\n' +
          '    return request;\n' +
          '}'),
    });

    const distribution = new cloudfront.Distribution(this, 'Distribution', {
      certificate: certificate,
      domainNames: [`viestinvalitys.${publicHostedZones[props.environmentName]}`],
      defaultRootObject: 'index.html',
      defaultBehavior: {
        origin: new cloudfront_origins.HttpOrigin(Fn.select(2, Fn.split('/', vastaanottoFunctionUrl.url)), {}),
        cachePolicy: noCachePolicy,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        originRequestPolicy: new cloudfront.OriginRequestPolicy(this, "LambdaOriginRequestPolicy", {
          originRequestPolicyName: `originRequestPolicy-${props.environmentName}-viestinvalitys`,
          cookieBehavior: cloudfront.OriginRequestCookieBehavior.all(),
          queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
          headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList("Accept", "Content-Type", "Content-Type-Original") // host header must be excluded???
        }),
        functionAssociations: [{
          function: lambdaHeaderFunction,
          eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
        }],
      },
      additionalBehaviors: {
        '/static/*': {
          origin: new cloudfront_origins.S3Origin(staticBucket, {
            originAccessIdentity: cloudfrontOAI
          }),
          cachePolicy: noCachePolicy,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        }
      }
    })

    // Route53 alias record for the CloudFront distribution
    new route53.ARecord(this, "SiteAliasRecord", {
      recordName: `viestinvalitys.${publicHostedZones[props.environmentName]}`,
      target: route53.RecordTarget.fromAlias(
          new route53targets.CloudFrontTarget(distribution)
      ),
      zone,
    });

    /**
     * Lähetyksen ajastus. Lambda joka kerran minuutissa puskee jonoon joukon sqs-viestejä joiden näkyvyys on ajatestettu
     * niin että uusi viesti tulee näkyviin joka n:äs sekunti
     */
    const ajastusAlias = getLambdaAsAlias(this,
        'Ajastus',
        false,
        `fi.oph.viestinvalitys.ajastus.LambdaHandler`,
        'ajastus/target/ajastus.jar',
        {
          ajastusSqsAccess,
        }, {
          "AJASTUS_QUEUE_URL": ajastusQueue.queueUrl,
        }, [])

    const ajastusRule = new aws_events.Rule(this, 'AjastusRule', {
      schedule: aws_events.Schedule.rate(cdk.Duration.minutes(1))
    });
    ajastusRule.addTarget(new targets.LambdaFunction(ajastusAlias));

    /**
     * Lähetyslambda, joka triggeröityy ajastusjonosta, ja joka lukee kannasta ja lähettää n-viestiä per invokaatio
     */
    const configurationSet = new ses.ConfigurationSet(this, 'ConfigurationSet', {
      configurationSetName: `${props.environmentName}-viestinvalityspalvelu`,
    });
    const eventDestination = new ses.CfnConfigurationSetEventDestination(this, 'EventDestination', {
      configurationSetName: configurationSet.configurationSetName,
      eventDestination: {
        snsDestination: {
          topicArn: monitorointiTopic.topicArn
        },
        enabled: true,
        matchingEventTypes: ['send', 'reject', 'bounce', 'complaint', 'delivery', 'deliveryDelay'],
      }
    })

    const lahetysAlias = getLambdaAsAlias(this,
        'Lahetys',
        true,
        `fi.oph.viestinvalitys.lahetys.LambdaHandler`,
        'lahetys/target/lahetys.jar',
        {
          attachmentS3Access,
          sesAccess,
          ssmAccess,
          ajastusSqsAccess,
          cloudwatchAccess,
        }, {
          AJASTUS_QUEUE_URL: ajastusQueue.queueUrl,
          MODE: 'TEST',
          FAKEMAILER_HOST: fakemailerHosts[props.environmentName],
          FAKEMAILER_PORT: '1025',
          ATTACHMENTS_BUCKET_NAME: `${props.environmentName}-viestinvalityspalvelu-attachments`,
          CONFIGURATION_SET_NAME: configurationSet.configurationSetName
        }, [
          postgresAccessSecurityGroup,
          fakemailerAccessSecurityGroup
        ])

    const eventSource = new eventsources.SqsEventSource(ajastusQueue);
    lahetysAlias.addEventSource(eventSource);

    /**
     * Lambda joka sisältää Flyway-tietokantamigraatiot
     */
    const migraatioAlias = getLambdaAsAlias(this,
        'Migraatio',
        true,
        `fi.oph.viestinvalitys.flyway.LambdaHandler`,
        'flyway/target/flyway.jar',
        {
          ssmAccess,
        }, {},
        [
          postgresAccessSecurityGroup
        ])

    /**
     * Lambda joka monitoroi BucketAV-skannerilta tulevia notifikaatioita ja päivittää niiden perusteella
     * liitetiedostojen metadaa
     */
    const skannausAlias = getLambdaAsAlias(this,
        'Skannaus',
        true,
        `fi.oph.viestinvalitys.skannaus.LambdaHandler`,
        'skannaus/target/skannaus.jar',
        {
          ssmAccess,
          skannausSqsAccess,
        }, {
          SKANNAUS_QUEUE_URL: skannausQueue.queueUrl
        },
        [
          postgresAccessSecurityGroup
        ])

    const skannausSqsEventSource = new eventsources.SqsEventSource(skannausQueue);
    skannausAlias.addEventSource(skannausSqsEventSource);

    /**
     * Lambda joka monitoroi SES:ltä tulevia notifikaatioita ja päivittää sen perusteella vastaanottajien tilaa
     */
    const monitorointiAlias = getLambdaAsAlias(this,
        'Monitorointi',
        true,
        `fi.oph.viestinvalitys.sesmonitorointi.LambdaHandler`,
        'ses-monitorointi/target/ses-monitorointi.jar',
        {
          ssmAccess,
          monitorointiSqsAccess,
        }, {
          "SES_MONITOROINTI_QUEUE_URL": monitorointiQueue.queueUrl
        },
        [
          postgresAccessSecurityGroup
        ])

    const monitorointiSqsEventSource = new eventsources.SqsEventSource(monitorointiQueue);
    monitorointiAlias.addEventSource(monitorointiSqsEventSource);

    /**
     * Lambda joka ajetaan ajastetusti ja poistaa viestit joiden säilytysaika on päättynyt
     */
    const siivousAlias = getLambdaAsAlias(this,
        'Siivous',
        true,
        `fi.oph.viestinvalitys.siivous.LambdaHandler`,
        'siivous/target/siivous.jar',
        {
          attachmentS3Access,
          ssmAccess,
        }, {
          ATTACHMENTS_BUCKET_NAME: `${props.environmentName}-viestinvalityspalvelu-attachments`
        },
        [
          postgresAccessSecurityGroup
        ])

    const siivousRule = new aws_events.Rule(this, 'SiivousRule', {
      schedule: aws_events.Schedule.rate(cdk.Duration.minutes(1))
    });
    siivousRule.addTarget(new targets.LambdaFunction(siivousAlias));

    /**
     * Dashboard
     */
    const vastaanottoNormaaliMetric = new cloudwatch.Metric({
      metricName: 'VastaanottojenMaara',
      namespace: 'Viestinvalitys',
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: {"Prioriteetti": "NORMAALI"},
      label: 'Vastaanotot Normaali',
      period: Duration.seconds(10),
    })
    const vastaanottoKorkeaMetric = new cloudwatch.Metric({
      metricName: 'VastaanottojenMaara',
      namespace: 'Viestinvalitys',
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: {"Prioriteetti": "KORKEA"},
      label: 'Vastaanotot Korkea',
      period: Duration.seconds(10),
    })

    const lahetysNormaaliMetric = new cloudwatch.Metric({
      metricName: 'LahetyksienMaara',
      namespace: 'Viestinvalitys',
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: {"Prioriteetti": "NORMAALI"},
      label: 'Lähetykset Normaali',
      period: Duration.seconds(10),
    })
    const lahetysKorkeaMetric = new cloudwatch.Metric({
      metricName: 'LahetyksienMaara',
      namespace: 'Viestinvalitys',
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: {"Prioriteetti": "KORKEA"},
      label: 'Lähetykset Korkea',
      period: Duration.seconds(10),
    })

    const lahetysWidget = new cloudwatch.GraphWidget({
      width: 24,
      title: 'Vastaanotot ja Lähetykset per/10s',
      left: [
        vastaanottoNormaaliMetric,
        vastaanottoKorkeaMetric,
        lahetysNormaaliMetric,
        lahetysKorkeaMetric,
      ]
    });

    const dashboard = new cloudwatch.Dashboard(this, 'Dashboard', {
      dashboardName: `${props.environmentName}-viestinvalitys`
    });
    dashboard.addWidgets(lahetysWidget);
  }
}
