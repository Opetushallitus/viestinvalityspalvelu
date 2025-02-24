import * as cdk from "aws-cdk-lib";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as constructs from "constructs";

export class PersistenssiStack extends cdk.Stack {
  readonly liitetiedostoBucket: s3.Bucket;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.liitetiedostoBucket = new s3.Bucket(this, "Attachments");
  }
}
