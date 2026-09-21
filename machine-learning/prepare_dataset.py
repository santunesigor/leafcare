import argparse
import json
from leafcare.common import load_config
from leafcare.dataset import prepare


def main():
    parser = argparse.ArgumentParser(description="Audita e separa imagens por classe, mantendo grupos de origem juntos.")
    parser.add_argument("--config", default="config.yaml")
    parser.add_argument("--audit-only", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(prepare(load_config(args.config), args.audit_only), ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Preparação bloqueada: {error}\n")


if __name__ == "__main__":
    main()
