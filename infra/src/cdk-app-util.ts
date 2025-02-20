import * as cdk from "aws-cdk-lib";
import * as codebuild from "aws-cdk-lib/aws-codebuild";
import * as codepipeline from "aws-cdk-lib/aws-codepipeline";
import * as codepipeline_actions from "aws-cdk-lib/aws-codepipeline-actions";
import * as codestarconnections from "aws-cdk-lib/aws-codestarconnections";
import * as constructs from "constructs";
import * as iam from "aws-cdk-lib/aws-iam";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as ssm from "aws-cdk-lib/aws-ssm";

class CdkAppUtil extends cdk.App {
  constructor(props: cdk.AppProps) {
    super(props);
    const env = {
      account: process.env.CDK_DEFAULT_ACCOUNT,
      region: process.env.CDK_DEFAULT_REGION,
    };
    new ContinuousDeploymentStack(this, "ContinuousDeploymentStack", {
      env,
    });
  }
}

class ContinuousDeploymentStack extends cdk.Stack {
  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    const githubConnection = new codestarconnections.CfnConnection(
      this,
      "GithubConnection",
      {
        connectionName: "GithubConnection",
        providerType: "GitHub",
      },
    );

    new ContinuousDeploymentPipelineStack(
      this,
      "HahtuvaContinuousDeploymentPipelineStack",
      "hahtuva",
      githubConnection,
      { owner: "Opetushallitus", name: "viestinvalityspalvelu", branch: "main" },
      props,
    );
    new ContinuousDeploymentPipelineStack(
      this,
      "DevContinuousDeploymentPipelineStack",
      "dev",
      githubConnection,
      { owner: "Opetushallitus", name: "viestinvalityspalvelu", branch: "green-hahtuva" },
      props,
    );
    new ContinuousDeploymentPipelineStack(
      this,
      "QaContinuousDeploymentPipelineStack",
      "qa",
      githubConnection,
      { owner: "Opetushallitus", name: "viestinvalityspalvelu", branch: "green-dev" },
      props,
    );
    new ContinuousDeploymentPipelineStack(
      this,
      "ProdContinuousDeploymentPipelineStack",
      "prod",
      githubConnection,
      { owner: "Opetushallitus", name: "viestinvalityspalvelu", branch: "green-qa" },
      props,
    );

    const radiatorAccountId = "905418271050";
    const radiatorReader = new iam.Role(this, "RadiatorReaderRole", {
      assumedBy: new iam.AccountPrincipal(radiatorAccountId),
      roleName: "RadiatorReader",
    });
    radiatorReader.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName(
        "AWSCodePipeline_ReadOnlyAccess",
      ),
    );
  }
}

type EnvironmentName = "hahtuva" | "dev" | "qa" | "prod";

type Repository = {
  owner: string;
  name: string;
  branch: string;
};

class ContinuousDeploymentPipelineStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    env: EnvironmentName,
    connection: codestarconnections.CfnConnection,
    repository: Repository,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);
    const capitalizedEnv = capitalize(env);

    const artifactBucket = new s3.Bucket(this, `ArtifactBucket`, {});
    const pipeline = new codepipeline.Pipeline(this, `DeployPipeline`, {
      pipelineName: `Deploy${capitalizedEnv}`,
      pipelineType: codepipeline.PipelineType.V1,
      artifactBucket,
    });

    const sourceOutput = new codepipeline.Artifact();
    const sourceAction =
      new codepipeline_actions.CodeStarConnectionsSourceAction({
        actionName: "Source",
        connectionArn: connection.attrConnectionArn,
        codeBuildCloneOutput: true,
        owner: repository.owner,
        repo: repository.name,
        branch: repository.branch,
        output: sourceOutput,
        triggerOnPush: ["hahtuva", "dev"].includes(env),
      });
    const sourceStage = pipeline.addStage({ stageName: "Source" });
    sourceStage.addAction(sourceAction);

    const deployProject = new codebuild.PipelineProject(this, `DeployProject`, {
      projectName: `Deploy${capitalizedEnv}`,
      concurrentBuildLimit: 1,
      environment: {
        buildImage: codebuild.LinuxArmBuildImage.AMAZON_LINUX_2_STANDARD_3_0,
        computeType: codebuild.ComputeType.SMALL,
        privileged: true,
      },
      environmentVariables: {
        CDK_DEPLOY_TARGET_ACCOUNT: {
          type: codebuild.BuildEnvironmentVariableType.PARAMETER_STORE,
          value: `/env/${env}/account_id`,
        },
        CDK_DEPLOY_TARGET_REGION: {
          type: codebuild.BuildEnvironmentVariableType.PLAINTEXT,
          value: "eu-west-1",
        },
        DOCKER_USERNAME: {
          type: codebuild.BuildEnvironmentVariableType.PARAMETER_STORE,
          value: "/docker/username",
        },
        DOCKER_PASSWORD: {
          type: codebuild.BuildEnvironmentVariableType.PARAMETER_STORE,
          value: "/docker/password",
        },
        SLACK_NOTIFICATIONS_CHANNEL_WEBHOOK_URL: {
          type: codebuild.BuildEnvironmentVariableType.PARAMETER_STORE,
          value: `/env/${env}/slack-notifications-channel-webhook`,
        },
      },
      buildSpec: codebuild.BuildSpec.fromObject({
        version: "0.2",
        env: {
          "git-credential-helper": "yes",
        },
        phases: {
          build: {
            commands: [
              `./deploy-${env}.sh && ./scripts/ci/tag-green-build-${env}.sh && ./scripts/ci/publish-release-notes-${env}.sh`,
            ],
          },
        },
      }),
    });

    const deploymentTargetAccount = ssm.StringParameter.valueFromLookup(
      this,
      `/env/${env}/account_id`,
    );
    const targetRegions = ["eu-west-1"];
    deployProject.role?.attachInlinePolicy(
      new iam.Policy(this, `Deploy${capitalizedEnv}Policy`, {
        statements: [
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ["sts:AssumeRole"],
            resources: targetRegions.flatMap((targetRegion) => [
              `arn:aws:iam::${deploymentTargetAccount}:role/cdk-hnb659fds-lookup-role-${deploymentTargetAccount}-${targetRegion}`,
              `arn:aws:iam::${deploymentTargetAccount}:role/cdk-hnb659fds-file-publishing-role-${deploymentTargetAccount}-${targetRegion}`,
              `arn:aws:iam::${deploymentTargetAccount}:role/cdk-hnb659fds-image-publishing-role-${deploymentTargetAccount}-${targetRegion}`,
              `arn:aws:iam::${deploymentTargetAccount}:role/cdk-hnb659fds-deploy-role-${deploymentTargetAccount}-${targetRegion}`,
            ]),
          }),
        ],
      }),
    );
    const deployAction = new codepipeline_actions.CodeBuildAction({
      actionName: "Deploy",
      input: sourceOutput,
      project: deployProject,
    });
    const deployStage = pipeline.addStage({ stageName: "Deploy" });
    deployStage.addAction(deployAction);
  }
}

function capitalize(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

const app = new CdkAppUtil({});
app.synth();
