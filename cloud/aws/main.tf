terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.4"
    }
  }
}

provider "aws" {
  region = var.region
}

data "aws_ssm_parameter" "windows_ami" {
  name = "/aws/service/ami-windows-latest/Windows_Server-2022-English-Full-Base"
}

data "aws_vpc" "default" {
  default = true
}

# ---------------------------------------------------------------- Network
locals {
  moonlight_tcp = [47984, 47989, 47990, 48010]
  moonlight_udp = [[47998, 48000], [48002, 48002], [48010, 48010]]
  open_ports    = var.vpn == "None" || var.expose_moonlight_publicly
  vpn_p2p = {
    Tailscale = [[41641, "udp"]]
    Headscale = [[41641, "udp"]]
    ZeroTier  = [[9993, "udp"]]
    EasyTier  = [[11010, "udp"], [11010, "tcp"]]
    Nebula    = [[4242, "udp"]]
  }
}

resource "aws_security_group" "gaming" {
  name_prefix = "${var.name}-"
  description = "Genshin cloud gaming host"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "RDP (setup only)"
    from_port   = 3389
    to_port     = 3389
    protocol    = "tcp"
    cidr_blocks = [var.allowed_cidr]
  }

  dynamic "ingress" {
    for_each = local.open_ports ? local.moonlight_tcp : []
    content {
      description = "Moonlight TCP"
      from_port   = ingress.value
      to_port     = ingress.value
      protocol    = "tcp"
      cidr_blocks = [var.allowed_cidr]
    }
  }

  dynamic "ingress" {
    for_each = local.open_ports ? local.moonlight_udp : []
    content {
      description = "Moonlight UDP"
      from_port   = ingress.value[0]
      to_port     = ingress.value[1]
      protocol    = "udp"
      cidr_blocks = [var.allowed_cidr]
    }
  }

  # Tailscale / ZeroTier direct (peer-to-peer) connections
  dynamic "ingress" {
    for_each = lookup(local.vpn_p2p, var.vpn, [])
    content {
      description = "${var.vpn} P2P"
      from_port   = ingress.value[0]
      to_port     = ingress.value[0]
      protocol    = ingress.value[1]
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------------------------------------------------------------- IAM (NVIDIA gaming driver bucket)
resource "aws_iam_role" "gaming" {
  name_prefix = "${var.name}-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "nvidia_driver" {
  role = aws_iam_role.gaming.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:ListBucket"]
      Resource = ["arn:aws:s3:::nvidia-gaming", "arn:aws:s3:::nvidia-gaming/*"]
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.gaming.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "gaming" {
  name_prefix = "${var.name}-"
  role        = aws_iam_role.gaming.name
}

# ---------------------------------------------------------------- Key pair (Windows password decrypt)
resource "tls_private_key" "rdp" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "rdp" {
  key_name_prefix = "${var.name}-"
  public_key      = tls_private_key.rdp.public_key_openssh
}

resource "local_sensitive_file" "rdp_key" {
  filename        = "${path.module}/${var.name}.pem"
  content         = tls_private_key.rdp.private_key_pem
  file_permission = "0600"
}

# ---------------------------------------------------------------- Instance
resource "aws_instance" "gaming" {
  ami                    = data.aws_ssm_parameter.windows_ami.value
  instance_type          = var.instance_type
  key_name               = aws_key_pair.rdp.key_name
  iam_instance_profile   = aws_iam_instance_profile.gaming.name
  vpc_security_group_ids = [aws_security_group.gaming.id]
  get_password_data      = true

  dynamic "instance_market_options" {
    for_each = var.use_spot ? [1] : []
    content {
      market_type = "spot"
      spot_options {
        spot_instance_type             = "persistent"
        instance_interruption_behavior = "stop"
      }
    }
  }

  root_block_device {
    volume_size = var.disk_gb
    volume_type = "gp3"
    iops        = 4000
    throughput  = 250
  }

  user_data = templatefile("${path.module}/user_data.ps1.tftpl", {
    repo_zip          = var.repo_zip_url
    stream_host       = var.stream_host
    launcher          = var.launcher
    vpn               = var.vpn
    tailscale_authkey = var.tailscale_authkey
    zerotier_network  = var.zerotier_network
    headscale_url     = var.headscale_url
    easytier_network  = var.easytier_network
    easytier_secret   = var.easytier_secret
    nebula_config_url = var.nebula_config_url
    extras            = join(" ", [for e in var.extras : "-${e}"])
  })

  tags = { Name = var.name }

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "gaming" {
  count    = var.static_ip ? 1 : 0
  instance = aws_instance.gaming.id
  domain   = "vpc"
  tags     = { Name = var.name }
}
