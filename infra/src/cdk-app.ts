import * as cdk from "aws-cdk-lib";
import * as alarms from "./alarms";
import * as vpc from "./vpc";
import * as dns from "./dns";
import * as database from "./database";
import * as ecs from "./ecs";
import * as migraatio from "./migraatio";
import * as persistenssi from "./persistenssi";

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
    new dns.DnsStack(this, "DnsStack", stackProps);
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
    new persistenssi.PersistenssiStack(this, "PeristenssiStack", stackProps);
  }
}

const app = new CdkApp({});
app.synth();
