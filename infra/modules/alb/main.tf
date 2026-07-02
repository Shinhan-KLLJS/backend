# ============================================================
# modules/alb/main.tf
# ALB: public subnet, HTTPS 수신 → EC2 HTTP 전달
# ============================================================

# ──────────────── ALB Security Group ────────────────
# 인터넷에서 443(HTTPS), 80(HTTP→HTTPS 리다이렉트) 허용
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "ALB - Allow inbound internet traffic"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP for redirect to HTTPS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound traffic to EC2"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-alb-sg"
    Project = var.project_name
  }
}

# ──────────────── ALB 본체 ────────────────
resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false        # 외부 인터넷 접근 허용
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids  # public subnet 2개

  tags = {
    Name    = "${var.project_name}-alb"
    Project = var.project_name
  }
}

# ──────────────── Target Group (EC2 등록 대상) ────────────────
resource "aws_lb_target_group" "ec2" {
  name     = "${var.project_name}-tg"
  port     = 8080        # Spring Boot 기본 포트
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  health_check {
    path                = "/actuator/health"  # Spring Actuator 헬스체크
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 10
  }

  tags = {
    Name    = "${var.project_name}-tg"
    Project = var.project_name
  }
}

# ──────────────── HTTP 리스너 (443으로 리다이렉트) ────────────────
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# ──────────────── HTTPS 리스너 ────────────────
# 인증서는 ALB와 같은 리전(ap-northeast-2)에서 발급된 ACM 인증서여야 함
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.acm_certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ec2.arn
  }
}
