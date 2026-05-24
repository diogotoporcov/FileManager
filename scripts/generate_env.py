import re
import secrets
import string
import argparse
import sys
import uuid
from enum import Enum
from typing import Match, Dict, Callable, Any, List, Optional
from pathlib import Path
from abc import ABC, abstractmethod

# Enums
class SecretAlgorithm(str, Enum):
    HEX = "hex"
    URLSAFE = "urlsafe"
    ALPHANUMERIC = "alphanumeric"
    LETTERS = "letters"
    NUMERIC = "numeric"
    PASSWORD = "password"
    UUID = "uuid"

    def __str__(self) -> str:
        return self.value

# Generators
class BaseSecretGenerator(ABC):
    """Base class for all secret generators."""
    def get_params(self) -> List[str]:
        """Return a list of supported parameter names."""
        return []

    @abstractmethod
    def generate(self, **kwargs) -> str:
        """Generate a secret string."""
        pass

class LengthSecretGenerator(BaseSecretGenerator):
    """Base class for generators that primarily use a 'length' parameter."""
    def get_params(self) -> List[str]:
        return ["length"]

    @abstractmethod
    def generate(self, length: int = 24) -> str:
        pass

class HexGenerator(LengthSecretGenerator):
    def generate(self, length: int = 24) -> str:
        return secrets.token_hex((length + 1) // 2)[:length]

class UrlsafeGenerator(LengthSecretGenerator):
    def generate(self, length: int = 24) -> str:
        return secrets.token_urlsafe(length)[:length]

class AlphanumericGenerator(LengthSecretGenerator):
    def generate(self, length: int = 24) -> str:
        alphabet = string.ascii_letters + string.digits
        return "".join(secrets.choice(alphabet) for _ in range(length))

class LettersGenerator(LengthSecretGenerator):
    def generate(self, length: int = 24) -> str:
        return "".join(secrets.choice(string.ascii_letters) for _ in range(length))

class NumericGenerator(LengthSecretGenerator):
    def generate(self, length: int = 24) -> str:
        return "".join(secrets.choice(string.digits) for _ in range(length))

class PasswordGenerator(LengthSecretGenerator):
    def generate(self, length: int = 24) -> str:
        alphabet = string.ascii_letters + string.digits + "!@#$%^&*()-_=+[]{}|;:,.<>?"
        return "".join(secrets.choice(alphabet) for _ in range(length))

class UuidGenerator(BaseSecretGenerator):
    def generate(self, **kwargs) -> str:
        return str(uuid.uuid4())

# Registry
DEFAULT_REGISTRY = {
    SecretAlgorithm.HEX: HexGenerator(),
    SecretAlgorithm.URLSAFE: UrlsafeGenerator(),
    SecretAlgorithm.ALPHANUMERIC: AlphanumericGenerator(),
    SecretAlgorithm.LETTERS: LettersGenerator(),
    SecretAlgorithm.NUMERIC: NumericGenerator(),
    SecretAlgorithm.PASSWORD: PasswordGenerator(),
    SecretAlgorithm.UUID: UuidGenerator(),
}

# Classes
class SecretGenerator:
    """
    Utility class to generate secure random strings for environment variables.
    """
    DEFAULT_LENGTH = 24

    def __init__(self, registry: Optional[Dict[SecretAlgorithm, BaseSecretGenerator]] = None):
        self._registry = registry or DEFAULT_REGISTRY

    def generate(self, config_str: str) -> str:
        """
        Parses a configuration string and returns a random secret.
        Format: algorithm[:pos_val][:name=val]...
        """
        parts = config_str.split(':')
        if not parts:
            return ""
        
        try:
            algo = SecretAlgorithm(parts[0].lower())
        except ValueError:
            raise ValueError(f"Unknown algorithm: {parts[0]}")
            
        instance = self._registry.get(algo)
        if not instance:
            raise ValueError(f"No generator registered for algorithm: {algo}")

        params = instance.get_params()
        kwargs = {"length": self.DEFAULT_LENGTH} if "length" in params else {}
        
        for i, opt in enumerate(parts[1:]):
            if "=" in opt:
                key, val = opt.split("=", 1)
                if key not in params:
                    raise ValueError(f"Unknown option '{key}' for algorithm '{algo}'. Valid options: {params}")

                kwargs[key] = self._cast_value(val, key)

            elif i < len(params):
                param_name = params[i]
                kwargs[param_name] = self._cast_value(opt, param_name)

            else:
                raise ValueError(
                    f"Too many positional options for algorithm '{algo}'. "
                    f"Expected at most {len(params)}, got {len(parts[1:])}."
                )

        return instance.generate(**kwargs)

    @staticmethod
    def _cast_value(val: str, param_name: str = "") -> Any:
        """Helper to cast string options to appropriate types."""
        if param_name == "length" or val.isdigit():
            try:
                return int(val)

            except ValueError:
                if param_name == "length":
                    raise ValueError(f"Invalid length value: {val}")
        
        normalized_val = val.lower()

        if normalized_val == "true":
            return True

        if normalized_val == "false":
            return False
            
        return val

# Functions
def process_env_file(source_path: Path, target_path: Path, force: bool = False) -> None:
    """
    Reads the source template, replaces placeholders, and writes to target.
    """
    if not source_path.exists():
        raise FileNotFoundError(f"Source file not found: {source_path}")
        
    if target_path.exists() and not force:
        print(f"Warning: Target file {target_path} already exists. Skipping.")
        return

    try:
        content = source_path.read_text(encoding="utf-8")

    except Exception as e:
        raise IOError(f"Failed to read source file: {e}")

    generator = SecretGenerator()

    def _replace(match: Match) -> str:
        config = match.group(1)
        return generator.generate(config)

    pattern = r"replace_me:([a-zA-Z0-9_:=]+)"
    new_content = re.sub(pattern, _replace, content)

    try:
        target_path.write_text(new_content, encoding="utf-8")
        print(f"Successfully generated {target_path}")

    except Exception as e:
        raise IOError(f"Failed to write target file: {e}")

# Main execution
def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate a .env file from a template by replacing placeholders with secure secrets."
    )
    parser.add_argument("source", type=Path, help="Template file (e.g., .env.example)")
    parser.add_argument("target", type=Path, help="Output file (e.g., .env.local)")
    parser.add_argument("-f", "--force", action="store_true", help="Overwrite existing target")
    
    args = parser.parse_args()
    
    try:
        process_env_file(args.source, args.target, args.force)
        return 0

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
