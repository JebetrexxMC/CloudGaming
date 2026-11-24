#!/usr/bin/env bash
# Start / stop / check the Genshin cloud VM created by cloud/aws.
# Stopping keeps the disk (game stays installed) and stops the hourly GPU charge.
set -euo pipefail

TF_DIR="$(cd "$(dirname "$0")/../cloud/aws" && pwd)"
REGION="$(terraform -chdir="$TF_DIR" output -raw region 2>/dev/null || echo "${AWS_REGION:-ap-southeast-1}")"
ID="$(terraform -chdir="$TF_DIR" output -raw instance_id)"

state() { aws ec2 describe-instances --region "$REGION" --instance-ids "$ID" \
  --query 'Reservations[0].Instances[0].[State.Name,PublicIpAddress]' --output text; }

case "${1:-status}" in
  start)
    aws ec2 start-instances --region "$REGION" --instance-ids "$ID" >/dev/null
    echo "Starting $ID ..."
    aws ec2 wait instance-running --region "$REGION" --instance-ids "$ID"
    read -r _ ip <<<"$(state)"
    echo "Running at $ip. Open Moonlight and pick 'Genshin Impact'."
    ;;
  stop)
    aws ec2 stop-instances --region "$REGION" --instance-ids "$ID" >/dev/null
    echo "Stopping $ID (disk kept)."
    ;;
  status)
    read -r s ip <<<"$(state)"
    echo "$ID  state=$s  ip=$ip"
    ;;
  *)
    echo "usage: $0 {start|stop|status}" >&2; exit 1 ;;
esac
