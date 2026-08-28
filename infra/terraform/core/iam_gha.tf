# The role GitHub Actions assumes over OIDC to (1) push images to ECR and (2) trigger the on-box
# redeploy via SSM. No long-lived AWS keys ever leave the account.

resource "aws_iam_role" "gha_ecr" {
  name = "delivery-glance-gha-ecr"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # BOTH subject forms, on purpose. This repo has immutable OIDC subjects enabled, so the
        # token 'sub' is the second (owner@id/repo@id) form. The first (plain owner/repo) form is
        # kept too so nothing breaks if that setting is ever turned off. Narrowing to the plain
        # form alone would break sts:AssumeRoleWithWebIdentity today. Do not remove either.
        StringLike = {
          "token.actions.githubusercontent.com:sub" = [
            "repo:${var.github_repo}:*",
            "repo:${var.github_repo_immutable}:*",
          ]
        }
      }
    }]
  })
}

# Push to the one ECR repository, and nothing else.
resource "aws_iam_role_policy" "gha_ecr_push" {
  name = "ecr-push"
  role = aws_iam_role.gha_ecr.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = aws_ecr_repository.delivery_glance.arn
      },
    ]
  })
}

# Trigger the redeploy: send AWS-RunShellScript to the one instance tagged delivery-glance and read
# the result back. This is how the deploy workflow rolls the box to a freshly-pushed image.
resource "aws_iam_role_policy" "gha_ssm_deploy" {
  name = "ssm-deploy"
  role = aws_iam_role.gha_ecr.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SendToRunShellScriptDoc"
        Effect   = "Allow"
        Action   = "ssm:SendCommand"
        Resource = "arn:aws:ssm:${var.region}::document/AWS-RunShellScript"
      },
      {
        Sid      = "SendToTaggedInstance"
        Effect   = "Allow"
        Action   = "ssm:SendCommand"
        Resource = "arn:aws:ec2:${var.region}:${var.account_id}:instance/*"
        Condition = {
          StringEquals = {
            "aws:ResourceTag/Name" = "delivery-glance"
          }
        }
      },
      {
        Sid    = "ReadCommandResult"
        Effect = "Allow"
        Action = [
          "ssm:GetCommandInvocation",
          "ssm:ListCommandInvocations",
        ]
        Resource = "*"
      },
      {
        Sid      = "FindInstanceByTag"
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
    ]
  })
}
