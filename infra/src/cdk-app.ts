import * as cdk from "aws-cdk-lib";
import * as alarms from "./alarms";
import * as vpc from "./vpc";

class CdkApp extends cdk.App {
  constructor(props: cdk.AppProps) {
    super(props);
    const stackProps = {
      env: {
        account: process.env.CDK_DEPLOY_TARGET_ACCOUNT,
        region: process.env.CDK_DEPLOY_TARGET_REGION,
      },
    };

    new alarms.AlarmStack(this, "AlarmStack", stackProps);
    new vpc.VpcStack(this, "VpcStack", stackProps);
  }
}

const app = new CdkApp({});
app.synth();