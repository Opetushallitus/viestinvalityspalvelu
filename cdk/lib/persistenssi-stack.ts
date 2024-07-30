import * as cdk from 'aws-cdk-lib';
import {CfnOutput, Duration} from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {BucketEncryption} from 'aws-cdk-lib/aws-s3';
import {Construct} from 'constructs';

interface ViestinValitysStackProps extends cdk.StackProps {
  environmentName: string;
}

export class PersistenssiStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ViestinValitysStackProps) {
    super(scope, id, props);

    // liitetiedostot
    const liitetiedostoBucket = new s3.Bucket(this, 'Attachments', {
      bucketName: `${props.environmentName}-viestinvalityspalvelu-attachments`,
      publicReadAccess: false,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: BucketEncryption.KMS_MANAGED,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });
    const s3Arn = new CfnOutput(this, "S3Arn", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-liitetiedosto-s3-arn`,
      description: 'Liitetiedostojen S3 arn',
      value: liitetiedostoBucket.bucketArn,
    });
  }
}
