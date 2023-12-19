#!/bin/bash

INSTANCE_NAME=$1-viestinvalitys-kuormatestaus
INSTANCE_ID=`aws-vault exec oph-dev -- aws ec2 describe-instances --filters Name=tag:Name,Values=$INSTANCE_NAME Name=instance-state-name,Values=running --output text --query 'Reservations[*].Instances[*].InstanceId'`

aws-vault exec oph-dev -- aws ec2-instance-connect ssh --instance-id $INSTANCE_ID --connection-type eice