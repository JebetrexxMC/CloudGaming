variable "name" {
  type    = string
  default = "genshin-cloud"
}

variable "region" {
  description = "ap-southeast-1 (Singapore) gives the lowest ping from the Philippines."
  type        = string
  default     = "ap-southeast-1"
}

variable "instance_type" {
  description = "g4dn.xlarge (T4) handles 1080p60. g5.xlarge (A10G) is faster and supports AV1."
  type        = string
  default     = "g4dn.xlarge"
}

variable "use_spot" {
  description = "Spot is much cheaper. AWS may stop it when capacity is short; your disk is kept."
  type        = bool
  default     = true
}

variable "disk_gb" {
  description = "Genshin needs about 100 GB plus room for updates."
  type        = number
  default     = 200
}

variable "allowed_cidr" {
  description = "Your public IP in CIDR form, e.g. 203.0.113.7/32 (curl ifconfig.me)."
  type        = string
}

variable "static_ip" {
  description = "Allocate an Elastic IP so the address survives stop/start."
  type        = bool
  default     = true
}

variable "stream_host" {
  type    = string
  default = "Apollo"
  validation {
    condition     = contains(["Apollo", "Sunshine"], var.stream_host)
    error_message = "stream_host must be Apollo or Sunshine."
  }
}

variable "launcher" {
  type    = string
  default = "HoYoPlay"
  validation {
    condition     = contains(["HoYoPlay", "Collapse", "Starward"], var.launcher)
    error_message = "launcher must be HoYoPlay, Collapse or Starward."
  }
}

variable "vpn" {
  description = "None, Tailscale or ZeroTier. With a VPN, Moonlight ports stay closed to the internet."
  type        = string
  default     = "None"
  validation {
    condition     = contains(["None", "Tailscale", "ZeroTier"], var.vpn)
    error_message = "vpn must be None, Tailscale or ZeroTier."
  }
}

variable "expose_moonlight_publicly" {
  description = "Also open Moonlight ports to allowed_cidr when a VPN is used."
  type        = bool
  default     = false
}

variable "tailscale_authkey" {
  description = "Reusable or ephemeral auth key from https://login.tailscale.com/admin/settings/keys"
  type        = string
  default     = ""
  sensitive   = true
}

variable "zerotier_network" {
  description = "16-character ZeroTier network ID from https://my.zerotier.com"
  type        = string
  default     = ""
}

variable "repo_zip_url" {
  type    = string
  default = "https://github.com/JebetrexxMC/CloudGaming/archive/refs/heads/main.zip"
}
