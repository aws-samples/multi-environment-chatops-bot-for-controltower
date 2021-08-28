#!/bin/bash
# Purpose: Install chatops-lex bot
# Author: Luiz Decaro {lddecaro@amazon.com} 
# ------------------------------------------

# $1 = # of seconds
# $@ = What to print after "Waiting n seconds"
countdown() {
  secs=$1
  shift
  msg=$@
  while [ $secs -gt 0 ]
  do
    printf "\r\033[KWaiting %.d seconds $msg" $((secs--))
    sleep 1
  done
  echo
}

red=`tput setaf 1`
green=`tput setaf 2`
reset=`tput sgr0`
orange=`tput setaf 3`
echo "${orange}------------------------------------------"
echo "ChatOps Lex Bot Installer (Phase 1)"
echo "------------------------------------------${reset}"
echo ""
projectSuffix=$(openssl rand -hex 2)
tempProjectName="chatops-lex-bot-"$projectSuffix
printf "Choose a project name for Phase 1 [$tempProjectName]: "
read projectName

if [ -z $projectName ]
then
    projectName=$tempProjectName
fi

#NOW=$(date +%F-%H-%M-%S)
printf "Choose a bucket name for source code upload [$projectName]: "
read bucketName

if [ -z $bucketName ]
then
    bucketName="$projectName"
fi

echo "${red}Attention: ${reset} Make sure you have configured your AWS CLI region! (use either 'aws configure' or set your 'AWS_DEFAULT_REGION' variable). "

if [ -z $AWS_DEFAULT_REGION ]
then
    region=$(aws configure get region)
    echo "Using default region from aws configure get region: $region"
else
    region=$AWS_DEFAULT_REGION
    echo "Using region from \$AWS_DEFAULT_REGION: $region"
fi

#region=$(aws ec2 describe-availability-zones --output text --query 'AvailabilityZones[0].[RegionName]')
lexBotRegion=''
printf "Choose a region where you want to install the chatops-lex-bot [$region]: "
read lexBotRegion

if [ -z "$lexBotRegion" ]
then
	lexBotRegion=$region
else
	region=$lexBotRegion
fi 

echo "Using region ${green} $region ${reset}"

if [[ $(aws s3api list-buckets --query "Buckets[?Name == '$bucketName'].[Name]" --output text) = $bucketName ]]; 
then
    echo "Bucket $bucketName is already created."
else
    echo "Creating a new S3 bucket on $region for your convenience..."
    aws s3 mb s3://$bucketName --region $region
    
    aws s3api wait bucket-exists --bucket $bucketName --region $region
	echo "Bucket $bucketName successfully created!"
fi

echo "Using project name....................${green}$projectName${reset}"
echo "Using bucket name.....................${green}$bucketName${reset}"
echo ""
echo "If these parameters are wrong press ${red}ctrl+c to stop now...${reset}"
countdown 10 "before continuing"

cd chatops-lex-bot
rm *.zip
rm -rf ./target
zip -qq -r "$projectName.zip" . -x "*.git*" -x "*.DS_Store"
aws s3 cp "$projectName.zip" "s3://$bucketName/$projectName.zip"
aws cloudformation package --template-file devops.yml --s3-bucket $bucketName --output-template-file devops-packaged.yml
aws cloudformation deploy --region $region --template-file devops-packaged.yml --stack-name "$projectName-cicd" --parameter-overrides ProjectName=$projectName CodeS3Bucket=$bucketName --capabilities CAPABILITY_NAMED_IAM

cd ..
export lexBotProjectSuffix=$projectSuffix
export lexBotRegion=$region
