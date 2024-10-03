#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { SovellusStack } from '../lib/sovellus-stack';
import {PersistenssiStack} from "../lib/persistenssi-stack";
import {LoadtestStack} from "../lib/loadtest-stack";
import {MigraatioStack} from "../lib/migraatio-stack";
import { AwsSolutionsChecks } from 'cdk-nag';

const app = new cdk.App();
const environmentName = app.node.tryGetContext("environment");

new SovellusStack(app, 'SovellusStack', {
    stackName: `${environmentName}-viestinvalitys-sovellus`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});

new PersistenssiStack(app, 'PersistenssiStack', {
    stackName: `${environmentName}-viestinvalitys-persistenssi`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});

new MigraatioStack(app, 'MigraatioStack', {
    stackName: `${environmentName}-viestinvalitys-migraatio`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});

new LoadtestStack(app, 'LoadtestStack', {
    stackName: `${environmentName}-viestinvalitys-loadtest`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});

cdk.Aspects.of(app).add(new AwsSolutionsChecks({verbose: true}))
