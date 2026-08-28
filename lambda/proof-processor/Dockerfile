# Container image for the proof-processor Lambda.
#
# Pillow ships native binaries, so the function must be packaged for the Lambda architecture — a
# plain zip of this directory does not carry them (see README). Building FROM the official Lambda
# base image, with `docker build --platform linux/arm64`, makes pip resolve the arm64 manylinux
# wheels that match the function's `architectures = ["arm64"]`. The Terraform module builds and
# pushes this image; nothing here is specific to how it is deployed.
FROM public.ecr.aws/lambda/python:3.12

# boto3 is provided by the runtime; requirements.txt pins only Pillow.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY handler.py ${LAMBDA_TASK_ROOT}/

CMD ["handler.lambda_handler"]
