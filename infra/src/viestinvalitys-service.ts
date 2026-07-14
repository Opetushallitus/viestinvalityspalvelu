import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as rds from "aws-cdk-lib/aws-rds";
import * as elasticloadbalancingv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as route53_targets from "aws-cdk-lib/aws-route53-targets";
import * as certificatemanager from "aws-cdk-lib/aws-certificatemanager";
import * as ecr_assets from "aws-cdk-lib/aws-ecr-assets";
import * as logs from "aws-cdk-lib/aws-logs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as path from "node:path";
import { getConfig, getEnvironment } from "./config";

const config = getConfig();

type ViestinvalitysServiceStackProps = cdk.StackProps & {
  vpc: ec2.IVpc;
  ecsCluster: ecs.Cluster;
  database: rds.DatabaseCluster;
  attachmentsBucket: s3.Bucket;
};

/**
 * ECS/ALB deployment of viestinvalitys-service (with the viestinvalitys-ui React app
 * bundled into the jar as static content). Modeled on tiedotuspalvelu/infra.
 *
 * Reuses the existing VPC, ECS cluster and Aurora database. A new hosted zone is
 * created for the base domain, and both the apex and the `nginx.` subdomain resolve
 * to a single internet-facing ALB that forwards to the Fargate service.
 */
export class ViestinvalitysServiceStack extends cdk.Stack {
  // viestinvalitys-service listens on this port (see application.properties).
  private readonly appPort = 8081;
  private readonly databaseName = "viestinvalityspalvelu";

  constructor(
    scope: constructs.Construct,
    id: string,
    props: ViestinvalitysServiceStackProps,
  ) {
    super(scope, id, props);

    const baseDomain = config.viestinvalitysPalveluDomain;
    const nginxDomain = `nginx.${baseDomain}`;

    const hostedZone = new route53.HostedZone(this, "HostedZone", {
      zoneName: baseDomain,
    });

    const logGroup = new logs.LogGroup(this, "AppLogGroup", {
      logGroupName: "Viestinvalitys/viestinvalitys-service",
      retention: logs.RetentionDays.FIVE_YEARS,
    });

    // Build the image from the repo-root Dockerfile (multi-stage: UI webpack build +
    // mvn package). __dirname is infra/src, so ../.. is the repository root.
    const dockerImage = new ecr_assets.DockerImageAsset(this, "AppImage", {
      directory: path.join(__dirname, "../.."),
      file: "Dockerfile",
      platform: ecr_assets.Platform.LINUX_ARM64,
      exclude: ["infra/cdk.out"],
    });

    const taskDefinition = new ecs.FargateTaskDefinition(
      this,
      "TaskDefinition",
      {
        cpu: 2048,
        memoryLimitMiB: 5120,
        runtimePlatform: {
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
        },
      },
    );

    taskDefinition.addContainer("AppContainer", {
      image: ecs.ContainerImage.fromDockerImageAsset(dockerImage),
      logging: new ecs.AwsLogDriver({ logGroup, streamPrefix: "app" }),
      environment: {
        // ENV selects the environment-specific configuration bundled in the jar at
        // classpath:/config/${ENV}.properties (see the repo-root Dockerfile entrypoint and
        // viestinvalitys-service/src/main/resources/config/*.properties). The CAS/virkailija
        // URLs and public domains live in those files, so they are no longer passed here.
        ENV: getEnvironment(),
        SERVER_PORT: this.appPort.toString(),
        AWS_REGION: this.region,
        // Override the datasource (application.properties defaults to localhost).
        SPRING_DATASOURCE_URL: `jdbc:postgresql://${props.database.clusterEndpoint.hostname}:${props.database.clusterEndpoint.port}/${this.databaseName}`,
        "spring.datasource.url": `jdbc:postgresql://${props.database.clusterEndpoint.hostname}:${props.database.clusterEndpoint.port}/${this.databaseName}`,
        "viestinvalitys.features.downloadViesti.enabled": config.features[
          "viestinvalitys.features.downloadViesti.enabled"
        ]
          ? "true"
          : "false",
        "aws.region": this.env.region,
        "attachments.bucket.name": props.attachmentsBucket.bucketName,
      },
      secrets: {
        "spring.datasource.username": ecs.Secret.fromSecretsManager(
          props.database.secret!,
          "username",
        ),
        "spring.datasource.password": ecs.Secret.fromSecretsManager(
          props.database.secret!,
          "password",
        ),
      },
      portMappings: [
        {
          name: "app",
          containerPort: this.appPort,
          appProtocol: ecs.AppProtocol.http,
        },
      ],
    });

    const service = new ecs.FargateService(this, "Service", {
      cluster: props.ecsCluster,
      taskDefinition,
      desiredCount: 2,
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      healthCheckGracePeriod: cdk.Duration.minutes(5),
      circuitBreaker: { enable: true },
    });

    const scaling = service.autoScaleTaskCount({
      minCapacity: 2,
      maxCapacity: 4,
    });
    scaling.scaleOnMetric("ServiceScaling", {
      metric: service.metricCpuUtilization(),
      scalingSteps: [
        { upper: 15, change: -1 },
        { lower: 50, change: +1 },
        { lower: 65, change: +2 },
        { lower: 80, change: +3 },
      ],
    });

    // Allow the service to reach the Aurora cluster on its default port.
    service.connections.allowToDefaultPort(props.database);

    // Allow viestinvalitys-service to access attachments bucket
    if (config.features["viestinvalitys.features.downloadViesti.enabled"]) {
      props.attachmentsBucket.grantRead(taskDefinition.taskRole);
    }

    const alb = new elasticloadbalancingv2.ApplicationLoadBalancer(
      this,
      "LoadBalancer",
      {
        vpc: props.vpc,
        internetFacing: true,
      },
    );

    new route53.ARecord(this, "ARecord", {
      zone: hostedZone,
      recordName: baseDomain,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(alb),
      ),
    });
    new route53.ARecord(this, "NginxARecord", {
      zone: hostedZone,
      recordName: nginxDomain,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(alb),
      ),
    });

    const certificate = new certificatemanager.Certificate(
      this,
      "Certificate",
      {
        domainName: baseDomain,
        subjectAlternativeNames: [nginxDomain],
        validation:
          certificatemanager.CertificateValidation.fromDns(hostedZone),
      },
    );

    const listener = alb.addListener("Listener", {
      protocol: elasticloadbalancingv2.ApplicationProtocol.HTTPS,
      port: 443,
      open: true,
      certificates: [certificate],
    });
    listener.addTargets("ServiceTarget", {
      port: this.appPort,
      protocol: elasticloadbalancingv2.ApplicationProtocol.HTTP,
      targets: [service],
      healthCheck: {
        enabled: true,
        interval: cdk.Duration.seconds(30),
        path: "/actuator/health",
        port: this.appPort.toString(),
      },
    });
  }
}
