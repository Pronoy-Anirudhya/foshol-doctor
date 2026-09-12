# Pushing to Docker Hub

The images are built and saved here. **Pushing is yours to run** — it needs your Docker Hub
credentials, and publishing is a decision, not a build step.

Replace `<namespace>` with your Docker Hub user or organisation.

```bash
# 1. log in (interactive, or use a read/write access token)
docker login

# 2. tag
docker tag foshol-doctor-aio:0.0.1-amd64      <namespace>/foshol-doctor-aio:0.0.1
docker tag foshol-doctor-app:0.0.1            <namespace>/foshol-doctor-app:0.0.1
docker tag foshol-doctor-sidecar:0.0.1        <namespace>/foshol-doctor-sidecar:0.0.1
docker tag foshol-doctor-sidecar:0.0.1-baked  <namespace>/foshol-doctor-sidecar:0.0.1-baked

# 3. push
docker push <namespace>/foshol-doctor-aio:0.0.1
docker push <namespace>/foshol-doctor-app:0.0.1
docker push <namespace>/foshol-doctor-sidecar:0.0.1
docker push <namespace>/foshol-doctor-sidecar:0.0.1-baked
```

Then DevOps pulls instead of loading a tarball:

```bash
docker pull <namespace>/foshol-doctor-aio:0.0.1
```

## Before you push — three things worth a moment

**Make the repository private.** The all-in-one image contains the backend jar, the demo
knowledge base, seeded credentials whose bcrypt is public in this repository, and a
redistributed MinIO binary under AGPL-3.0. Docker Hub's free tier allows **one** private
repository, and this would be three. Either pay for the plan, push only `foshol-doctor-aio`
privately, or use `ghcr.io` where private packages are free and unlimited:

```bash
echo "$GH_PAT" | docker login ghcr.io -u <github-user> --password-stdin
docker tag foshol-doctor-aio:0.0.1-amd64 ghcr.io/<org>/foshol-doctor-aio:0.0.1
docker push ghcr.io/<org>/foshol-doctor-aio:0.0.1
```

**These are `linux/amd64` only.** Confirm the VM with `uname -m` (expect `x86_64`). An arm64
VM needs a rebuild — see [`../deploy/aio/README.md`](../deploy/aio/README.md).

**The upload is ~3.6 GB** for the all-in-one image. Largest single layer is ~1.9 GB (LaBSE),
well inside any registry limit, but a slow link will take a while. `docker push` resumes
per-layer if interrupted.

## If you would rather not use a registry

Copy the tarball and load it on the VM:

```bash
scp foshol-doctor-aio-0.0.1-linux-amd64.tar.gz* vm:/var/tmp/
ssh vm 'cd /var/tmp && shasum -a 256 -c foshol-doctor-aio-0.0.1-linux-amd64.tar.gz.sha256 \
  && gunzip -c foshol-doctor-aio-0.0.1-linux-amd64.tar.gz | docker load'
```

Run instructions are in [`README.md`](README.md).
