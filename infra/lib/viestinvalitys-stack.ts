import * as cdk from 'aws-cdk-lib';
import {Duration} from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import {Architecture, FunctionUrlAuthType} from 'aws-cdk-lib/aws-lambda';
import {Construct} from 'constructs';
import path = require("path");

export class ViestinValitysStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

/*
    // korkean prioriteetin jono
    const highPriority = new sqs.Queue(this, 'priority-high', {
       visibilityTimeout: cdk.Duration.seconds(300)
    });

    // liitetiedostot
    const attachmentBucket = new s3.Bucket(this, 'attachments');
*/

    const vastaanotto = new lambda.Function(this, 'vastaanotto', {
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitus.vastaanotto.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../vastaanotto/target/vastaanotto-0.1-SNAPSHOT.jar')),
      timeout: Duration.seconds(30),
      memorySize: 512,
      architecture: Architecture.X86_64
    });

    // Enable SnapStart
    const cfnFunction = vastaanotto.node.defaultChild as lambda.CfnFunction;
    cfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const version = vastaanotto.currentVersion;
    const alias = new lambda.Alias(this, 'LambdaAlias', {
      aliasName: 'Test',
      version,
    });

    const versionUrl = alias.addFunctionUrl({
      authType: FunctionUrlAuthType.NONE
    })
  }
}
