#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VastaanottoStack } from '../lib/vastaanotto-stack';
import {PersistenssiStack} from "../lib/persistenssi-stack";
import {LoadtestStack} from "../lib/loadtest-stack";

const app = new cdk.App();
const environmentName = app.node.tryGetContext("environment");

new VastaanottoStack(app, 'ViestinValitysStack', { // TODO: muuta
    stackName: `${environmentName}-viestinvalitys`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});

new PersistenssiStack(app, 'PersistenssiStack', {
    stackName: `${environmentName}-viestinvalitys-persistenssi`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});

new LoadtestStack(app, 'LoadtestStack', {
    stackName: `${environmentName}-viestinvalitys-loadtest`,
    environmentName: environmentName,
    env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});