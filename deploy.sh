#!/bin/bash
# Purpose: Install chatops-lex
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
  printf ""
  echo
}

red=`tput setaf 1`
green=`tput setaf 2`
reset=`tput sgr0`
orange=`tput setaf 3`

source ./chatops-lex-bot/deploy.sh
projectName=''
bucketName=''

echo "${green}------------------------------------------"
echo "PHASE 1 COMPLETE: ChatOps Lex Bot is installed"
echo "------------------------------------------${reset}"
echo ""

echo "${orange}------------------------------------------"
echo "Starting ChatOps Installer (Phase 2)"
echo "------------------------------------------${reset}"
echo ""

#tempProjectName="chatops-lex-"$(openssl rand -hex 2)
tempProjectName="chatops-lex-"$lexBotProjectSuffix

printf "Choose a project name for Phase 2: [$tempProjectName]: "
read projectName

if [ -z $projectName ]
then
    projectName=$tempProjectName
fi

NOW=$(date +%F-%H-%M-%S)
printf "Choose a bucket name for source code upload [$projectName]: "
read bucketName

if [ -z $bucketName ]
then
    bucketName="$projectName"
fi

echo "ChatOps-Lex-Bot is already deployed in region${orange} ${lexBotRegion} ${reset}"

printf "Choose a mailbox to receive approval e-mails for Account vending requests: "
read mailbox

if [ -z $AWS_DEFAULT_REGION ]
	then
	    region=$(aws configure get region)
	    echo "Using default region from aws configure get region:  $region"
	else
	    region=$AWS_DEFAULT_REGION
	    echo "Using region from \$AWS_DEFAULT_REGION variable: $region"
	fi

#region=$(aws ec2 describe-availability-zones --output text --query 'AvailabilityZones[0].[RegionName]')

printf "Choose the AWS region where your vending machine is installed [$region]: "
read chatOpsRegion
if [ -z "$chatOpsRegion" ]
then
	chatOpsRegion=$region
else
	region=$chatOpsRegion
fi
echo "Using region ${green} $region ${reset}"

##printf "Please tell us in which region the lex bot is deployed: "
##read lexbotregion


if [[ $(aws s3api list-buckets --query "Buckets[?Name == '$bucketName'].[Name]" --output text) = $bucketName ]]; 
then
    echo "Bucket $bucketName is already created." ;
else
    echo "Creating a new S3 bucket on $region for your convenience..."
    aws s3 mb s3://$bucketName --region $region
    
    aws s3api wait bucket-exists --bucket $bucketName --region $region
	echo "Bucket $bucketName successfully created!"
fi

echo "Trying to find the ${green}AWS Control Tower Account Factory Portfolio${reset}"
echo ""
portfolioName="AWS Control Tower Account Factory Portfolio"
portfolioId="$(aws servicecatalog list-portfolios --query "PortfolioDetails[?DisplayName == '$portfolioName'].[Id]" --output text)"
if [[ -z $portfolioId ]]
then
   echo "Could not find portfolio named $portfolioName. Is Control Tower installed ? Is this the Master Account ?"
   echo "Exiting..."
   exit 1
fi

echo "Using project name....................${green}$projectName${reset}"
echo "Using bucket name.....................${green}$bucketName${reset}"
echo "Using mailbox for approvals...........${green}$mailbox${reset}"
echo "Using lexbot region...................${green}$lexBotRegion${reset}"
echo "Using service catalog portfolio-id....${green}$portfolioId${reset}"
echo ""
echo "If these parameters are wrong press ${red}ctrl+c to stop now...${reset}"
countdown 10 "before continuing"

if command -v mvn  &> /dev/null
then
    mvn clean 
fi

rm *.zip
rm -rf ./target
zip -qq -r "$projectName.zip" . -x "*.git*" -x "*.DS_Store"
aws s3 cp "$projectName.zip" "s3://$bucketName/$projectName.zip"
aws cloudformation package --template-file devops.yml --s3-bucket $bucketName --output-template-file devops-packaged.yml
aws cloudformation deploy --region $region --template-file devops-packaged.yml --stack-name "$projectName-cicd" --parameter-overrides ProjectName=$projectName CodeS3Bucket=$bucketName PortfolioId=$portfolioId ApprovalMailbox=$mailbox LexBotRegion=$lexBotRegion --capabilities CAPABILITY_NAMED_IAM


echo "${green}------------------------------------------"
echo "PHASE 2 COMPLETE: ChatOps Pipeline is installed"
echo "ChatOps Lex Pipeline and Chatops Lex Bot Pipelines successfully installed"
echo "------------------------------------------${reset}"
echo ""

