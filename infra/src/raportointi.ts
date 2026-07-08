import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as ecr_assets from "aws-cdk-lib/aws-ecr-assets";
import * as elbv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as logs from "aws-cdk-lib/aws-logs";
import * as path from "node:path";
import * as config from "./config";

export class RaportointiStack extends cdk.Stack {
  readonly loadBalancer: elbv2.IApplicationLoadBalancer;

  constructor(
    scope: constructs.Construct,
    id: string,
    ecsCluster: ecs.ICluster,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    const domainName = `viestinvalitys.${config.getConfig().opintopolkuDomainName}`;

    const dockerImage = new ecr_assets.DockerImageAsset(this, "Image", {
      directory: path.join(__dirname, "../../viestinvalitys-raportointi"),
      file: "Dockerfile",
      platform: ecr_assets.Platform.LINUX_ARM64,
    });

    const logGroup = new logs.LogGroup(this, "LogGroup", {
      logGroupName: "/ecs/raportointi",
      retention: logs.RetentionDays.FIVE_YEARS,
    });

    const taskDefinition = new ecs.FargateTaskDefinition(
      this,
      "TaskDefinition",
      {
        cpu: 512,
        memoryLimitMiB: 1024,
        runtimePlatform: {
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        },
      },
    );

    taskDefinition.addContainer("Container", {
      image: ecs.ContainerImage.fromDockerImageAsset(dockerImage),
      logging: new ecs.AwsLogDriver({ logGroup, streamPrefix: "raportointi" }),
      environment: {
        VIRKAILIJA_URL: `https://virkailija.${config.getConfig().opintopolkuDomainName}`,
        VIESTINTAPALVELU_URL: `https://${domainName}`,
        LOGIN_URL: `https://${domainName}/raportointi/login`,
        PORT: "8080",
        HOSTNAME: "0.0.0.0",
        COOKIE_NAME: "JSESSIONID",
        FEATURE_DOWNLOAD_VIESTI_ENABLED: `${config.getConfig().features["viestinvalitys.features.downloadViesti.enabled"]}`,
      },
      portMappings: [{ containerPort: 8080 }],
    });

    const service = new ecs.FargateService(this, "Service", {
      cluster: ecsCluster,
      serviceName: "raportointi",
      taskDefinition,
      desiredCount: 2,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
    });

    const loadBalancer = new elbv2.ApplicationLoadBalancer(
      this,
      "LoadBalancer",
      {
        vpc: ecsCluster.vpc,
        internetFacing: false,
      },
    );

    const listener = loadBalancer.addListener("Listener", {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
    });

    listener.addTargets("Target", {
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [service],
      healthCheck: {
        path: "/raportointi/api/health",
        healthyHttpCodes: "200",
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
      },
    });

    this.loadBalancer = loadBalancer;
  }
}
