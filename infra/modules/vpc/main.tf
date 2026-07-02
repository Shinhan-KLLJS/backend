# ============================================================
# modules/vpc/main.tf
# VPC, Public/Private 서브넷, 라우팅 테이블, Internet Gateway
# ============================================================

# ──────────────── VPC ────────────────
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true   # RDS 등 VPC 내부 DNS 해석에 필요
  enable_dns_support   = true

  tags = {
    Name    = "${var.project_name}-vpc"
    Project = var.project_name
  }
}

# ──────────────── Internet Gateway ────────────────
# Public subnet이 인터넷과 통신하기 위한 게이트웨이
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name    = "${var.project_name}-igw"
    Project = var.project_name
  }
}

# ──────────────── Public Subnet (ALB 위치) ────────────────
resource "aws_subnet" "public" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  # 10.0.0.0/24, 10.0.1.0/24 ...
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = var.availability_zones[count.index]

  # ALB는 퍼블릭 IP 필요
  map_public_ip_on_launch = true

  tags = {
    Name    = "${var.project_name}-public-${count.index + 1}"
    Type    = "public"
    Project = var.project_name
  }
}

# ──────────────── Private Subnet (RDS 전용) ────────────────
resource "aws_subnet" "private" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  # 10.0.10.0/24, 10.0.11.0/24 ...
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name    = "${var.project_name}-private-${count.index + 1}"
    Type    = "private"
    Project = var.project_name
  }
}

# ──────────────── 라우팅 테이블 (Public) ────────────────
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  # 모든 외부 트래픽 → Internet Gateway
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name    = "${var.project_name}-rt-public"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ──────────────── 라우팅 테이블 (Private) ────────────────
# RDS는 인터넷 경로가 필요 없으므로 NAT Gateway 없이 격리만 유지
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name    = "${var.project_name}-rt-private"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
