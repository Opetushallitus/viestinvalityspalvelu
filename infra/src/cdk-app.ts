import * as cdk from "aws-cdk-lib";
import * as alarms from "./alarms";
import * as vpc from "./vpc";
import * as dns from "./dns";
import * as database from "./database";
import * as ecs from "./ecs";
import * as migraatio from "./migraatio";
import * as persistenssi from "./persistenssi";
import * as sovellus from "./sovelllus";
import * as ses from "./ses";

class CdkApp extends cdk.App {
  constructor(props: cdk.AppProps) {
    super(props);
    const stackProps = {
      env: {
        account: process.env.CDK_DEPLOY_TARGET_ACCOUNT,
        region: process.env.CDK_DEPLOY_TARGET_REGION,
      },
    };

    const alarmStack = new alarms.AlarmStack(this, "AlarmStack", stackProps);
    const vpcStack = new vpc.VpcStack(this, "VpcStack", stackProps);
    const dnsStack = new dns.DnsStack(this, "DnsStack", stackProps);
    const ecsStack = new ecs.EcsStack(
      this,
      "EcsStack",
      vpcStack.vpc,
      stackProps,
    );
    const databaseStack = new database.DatabaseStack(
      this,
      "DatabaseStack",
      vpcStack.vpc,
      vpcStack.bastion,
      ecsStack.ecsCluster,
      alarmStack.alarmTopic,
      stackProps,
    );
    new migraatio.MigraatioStack(
      this,
      "MigraatioStack",
      vpcStack.vpc,
      databaseStack.database,
      databaseStack.accessForLambda,
      stackProps,
    );
    const sesStack = new ses.SESStack(
      this,
      "SESStack",
      dnsStack.hostedZone,
      stackProps,
    );
    const persistenssiStack = new persistenssi.PersistenssiStack(
      this,
      "PeristenssiStack",
      stackProps,
    );
    new sovellus.SovellusStack(
      this,
      "SovellusStack",
      vpcStack.vpc,
      dnsStack.hostedZone,
      dnsStack.opintopolkuHostedZone,
      databaseStack.database,
      databaseStack.accessForLambda,
      persistenssiStack.liitetiedostoBucket,
      sesStack.monitorointiQueue,
      sesStack.identity,
      sesStack.configurationSet,
      stackProps,
    );
  }
}

const app = new CdkApp({});
app.synth();
