import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {AmazonLinuxCpuType} from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3assets from "aws-cdk-lib/aws-s3-assets";
import {Construct} from 'constructs';
import {NagSuppressions} from "cdk-nag";

interface LoadtestStackProps extends cdk.StackProps {
  environmentName: string;
}

/**
 * Luo valmiiksi konfiguroidun kuormatestausinstanssin
 */
export class LoadtestStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: LoadtestStackProps) {
    super(scope, id, props);

    // ympäristökohtainen vpc, jostain syystä instanssin luonti ei onnistu jos sisältää kaikki subnetit
    const vpc = ec2.Vpc.fromVpcAttributes(this, "LoadtestingVPC", {
      vpcId: cdk.Fn.importValue(`${props.environmentName}-Vpc`),
      availabilityZones: [
        'eu-west-1a',
      ],
      privateSubnetIds: [
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet1`),
      ],
    });

    // tallennetaan k6-skripti instanssille
    const packageJsonS3Asset = new s3assets.Asset(this, 'PackageJsonAsset', {
      path: '../kuormatestaus/package.json',
    });
    const testdataScriptS3Asset = new s3assets.Asset(this, 'TestDataScriptAsset', {
      path: '../kuormatestaus/generate_test_data.js',
    });
    const loadtestScriptS3Asset = new s3assets.Asset(this, 'LoadTestScriptAsset', {
      path: '../kuormatestaus/script.js',
    });
    const runnerS3Asset = new s3assets.Asset(this, 'RunnerAsset', {
      path: '../kuormatestaus/run.sh',
    });
    const initData = ec2.CloudFormationInit.fromElements(
        ec2.InitFile.fromExistingAsset('/home/ssm-user/package.json', packageJsonS3Asset, {}),
        ec2.InitFile.fromExistingAsset('/home/ssm-user/generate_test_data.js', testdataScriptS3Asset, {}),
        ec2.InitFile.fromExistingAsset('/home/ssm-user/script.js', loadtestScriptS3Asset, {}),
        ec2.InitFile.fromExistingAsset('/home/ssm-user/run.sh', runnerS3Asset, {
          mode: '000770',
        })
    );

    // annetaan instanssille oikeus hakea kuormatestikäyttäjän salasana
    const ssmAccess = new iam.PolicyDocument({
      statements: [new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'ssm:GetParameter',
        ],
        resources: [`arn:aws:ssm:eu-west-1:153563371259:parameter/${props.environmentName}/viestinvalitys/loadtest-password`],
      })
      ],
    })
    const loadtestingRole = new iam.Role(this, 'InstanceRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      inlinePolicies: {
        ssmAccess,
      }
    })

    const loadtestingInstance = new ec2.Instance(this, 'targetInstance', {
      vpc: vpc,
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.M7A, ec2.InstanceSize.LARGE),
      machineImage: new ec2.AmazonLinuxImage({
        generation: ec2.AmazonLinuxGeneration.AMAZON_LINUX_2023,
        cpuType: AmazonLinuxCpuType.X86_64, // k6-pakettia ei tarjolla armille
      }),
      init: initData,
      instanceName: `${props.environmentName}-viestinvalitys-kuormatestaus`,
      vpcSubnets: {
        subnets: vpc.privateSubnets
      },
      associatePublicIpAddress: false,
      role: loadtestingRole,
      ssmSessionPermissions: true,
    });
    loadtestingInstance.addUserData(
        // määritellään ympäristö loginin yhteydessä
        `echo sudo chown -R ssm-user:ssm-user /home/ssm-user >> /home/ssm-user/.bashrc; ` +
        `echo export VIESTINVALITYS_ENVIRONMENT=${props.environmentName} >> /home/ssm-user/.bashrc; ` +
        `echo export VIESTINVALITYS_PASSWORD=\\\`aws ssm get-parameter --name /${props.environmentName}/viestinvalitys/loadtest-password --with-decryption --output text --query \\'Parameter.Value\\'\\\` >> /home/ssm-user/.bashrc; ` +
        `echo cd /home/ssm-user >> /home/ssm-user/.bashrc;`
    )

    NagSuppressions.addStackSuppressions(this, [
      { id: 'AwsSolutions-IAM4', reason: 'Load testing data contains no secrets'},
      { id: 'AwsSolutions-IAM5', reason: 'Load testing data contains no secrets'},
      { id: 'AwsSolutions-EC26', reason: 'Load testing data contains no secrets'},
      { id: 'AwsSolutions-EC28', reason: 'Load testing does not need monitoring'},
      { id: 'AwsSolutions-EC29', reason: 'Load testing does not need monitoring'},
    ])
  }
}