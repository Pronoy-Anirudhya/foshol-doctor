"""Turn a supervisord FATAL into container exit.

A program that exhausts startretries leaves a half-dead container whose PID 1 is still
alive: --restart=unless-stopped never fires and the failure hides behind an unhealthy
status. Stopping PID 1 surfaces it to the orchestrator instead.
"""

import os
import signal
import sys


def main() -> None:
    while True:
        sys.stdout.write("READY\n")
        sys.stdout.flush()
        line = sys.stdin.readline()
        headers = dict(pair.split(":", 1) for pair in line.split())
        payload = sys.stdin.read(int(headers["len"]))
        sys.stderr.write("fatalexit: %s -> stopping container\n" % payload.strip())
        sys.stderr.flush()
        sys.stdout.write("RESULT 2\nOK")
        sys.stdout.flush()
        os.kill(1, signal.SIGTERM)  # supervisord is PID 1


if __name__ == "__main__":
    main()
