#!/bin/bash

INSTANCE_NAME=$1-viestinvalitys-kuormatestaus
INSTANCE_ID=`aws-vault exec oph-dev -- aws ec2 describe-instances --filters Name=tag:Name,Values=$INSTANCE_NAME Name=instance-state-name,Values=running --output text --query 'Reservations[*].Instances[*].InstanceId'`

#aws ec2-instance-connect send-ssh-public-key --profile oph-dev --instance-id $INSTANCE_ID --instance-os-user ssm-user --ssh-public-key file://~/.ssh/id_rsa.pub
#aws ssm start-session --target $INSTANCE_ID --profile oph-dev --document-name AWS-StartSSHSession --parameters 'portNumber=%p'
#aws-vault exec oph-dev -- aws ssm start-session --target $INSTANCE_ID --region eu-west-1
aws-vault exec oph-dev -- aws ssm start-session --target $INSTANCE_ID

