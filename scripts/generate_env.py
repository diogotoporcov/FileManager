import argparse
import re
import secrets
import string
import uuid
from abc import ABC, abstractmethod
from collections.abc import Mapping, MutableMapping, Sequence
from enum import Enum
from pathlib import Path

import sys
from typing import TypeAlias

SecretOptionValue: TypeAlias = str | int | bool


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
    def get_params(self) -> Sequence[str]:
        """Return a list of supported parameter names."""
        return []

    @abstractmethod
    def generate(self, **kwargs: SecretOptionValue) -> str:
        """Generate a secret string."""
        pass

class LengthSecretGenerator(BaseSecretGenerator):
    """Base class for generators that primarily use a 'length' parameter."""
    def get_params(self) -> Sequence[str]:
        return ["length"]

    @staticmethod
    def _get_length(kwargs: Mapping[str, SecretOptionValue]) -> int:
        length = kwargs.get("length", 24)

        if type(length) is not int:
            raise ValueError(f"Invalid length value: {length}")

        return length

class HexGenerator(LengthSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        length = self._get_length(kwargs)

        return secrets.token_hex((length + 1) // 2)[:length]

class UrlsafeGenerator(LengthSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        length = self._get_length(kwargs)

        return secrets.token_urlsafe(length)[:length]

class AlphanumericGenerator(LengthSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        length = self._get_length(kwargs)
        alphabet = string.ascii_letters + string.digits

        return "".join(secrets.choice(alphabet) for _ in range(length))

class LettersGenerator(LengthSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        length = self._get_length(kwargs)

        return "".join(secrets.choice(string.ascii_letters) for _ in range(length))

class NumericGenerator(LengthSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        length = self._get_length(kwargs)

        return "".join(secrets.choice(string.digits) for _ in range(length))

class PasswordGenerator(LengthSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        length = self._get_length(kwargs)
        alphabet = string.ascii_letters + string.digits + "!@#$%^&*()-_=+[]{}|;:,.<>?"

        return "".join(secrets.choice(alphabet) for _ in range(length))

class UuidGenerator(BaseSecretGenerator):
    def generate(self, **kwargs: SecretOptionValue) -> str:
        return str(uuid.uuid4())

# Registry
class SecretGenerator:
    """
    Utility class to generate secure random strings for environment variables.
    """
    DEFAULT_LENGTH = 24

    def __init__(self, registry: Mapping[SecretAlgorithm, BaseSecretGenerator] | None = None):
        self._registry = registry or {
            SecretAlgorithm.HEX: HexGenerator(),
            SecretAlgorithm.URLSAFE: UrlsafeGenerator(),
            SecretAlgorithm.ALPHANUMERIC: AlphanumericGenerator(),
            SecretAlgorithm.LETTERS: LettersGenerator(),
            SecretAlgorithm.NUMERIC: NumericGenerator(),
            SecretAlgorithm.PASSWORD: PasswordGenerator(),
            SecretAlgorithm.UUID: UuidGenerator(),
        }

    def generate(self, config_str: str) -> str:
        """
        Parses a configuration string and returns a random secret.
        Format: algorithm[:pos_val][:name=val]...
        """
        parts = config_str.split(':')
        if not parts:
            return ""
        
        algo = self._parse_algo(parts[0])
        instance = self._get_instance(algo)
        params = instance.get_params()
        kwargs = self._parse_kwargs(algo, params, parts[1:])

        return instance.generate(**kwargs)

    @staticmethod
    def _parse_algo(name: str) -> SecretAlgorithm:
        """Parses the algorithm name into a SecretAlgorithm enum."""
        try:
            return SecretAlgorithm(name.lower())

        except ValueError:
            raise ValueError(f"Unknown algorithm: {name}")

    def _get_instance(self, algo: SecretAlgorithm) -> BaseSecretGenerator:
        """Retrieves the generator instance from the registry."""
        instance = self._registry.get(algo)
        if not instance:
            raise ValueError(f"No generator registered for algorithm: {algo}")

        return instance

    def _parse_kwargs(
        self,
        algo: SecretAlgorithm,
        params: Sequence[str],
        options: Sequence[str],
    ) -> Mapping[str, SecretOptionValue]:
        """Parses positional and named options into a keyword argument dictionary."""
        kwargs: MutableMapping[str, SecretOptionValue] = {"length": self.DEFAULT_LENGTH} if "length" in params else {}
        
        for i, opt in enumerate(options):
            if "=" in opt:
                key, val = opt.split("=", 1)
                if key not in params:
                    raise ValueError(f"Unknown option '{key}' for algorithm '{algo}'. Valid options: {params}")

                kwargs[key] = self._cast_value(val, key)
                continue

            if i >= len(params):
                raise ValueError(
                    f"Too many positional options for algorithm '{algo}'. "
                    f"Expected at most {len(params)}, got {len(options)}."
                )

            param_name = params[i]
            kwargs[param_name] = self._cast_value(opt, param_name)
            
        return kwargs

    @staticmethod
    def _cast_value(val: str, param_name: str = "") -> SecretOptionValue:
        """Helper to cast string options to appropriate types."""
        if param_name == "length":
            if not val.isdigit():
                raise ValueError(f"Invalid length value: {val}")

            return int(val)

        if val.isdigit():
            return int(val)
        
        return {"true": True, "false": False}.get(val.lower(), val)

class EnvTemplateProcessor:
    """
    Parses .env files and replaces placeholders in values while preserving structure and comments.
    """
    def __init__(self, generator: SecretGenerator):
        self.generator = generator
        # Regex to identify environment variable assignments
        # Group 1: Leading space and optional 'export'
        # Group 2: The key name
        # Group 3: The equal sign and surrounding whitespace
        # Group 4: The value part (handles optional quotes and avoids trailing comments)
        # Group 5: The rest of the line (trailing comments)
        self.assignment_pattern = re.compile(
            r"^(\s*(?:export\s+)?)([A-Za-z0-9_]+)(\s*=\s*)((?:'[^']*'|\"[^\"]*\"|[^#])+)?(.*)$"
        )
        self.placeholder_pattern = re.compile(r"replace_me:([a-zA-Z0-9_:=]+)")

    def process(self, content: str) -> str:
        """Process the content of a .env file line by line."""
        lines = content.splitlines(keepends=True)
        new_lines = []

        for line in lines:
            line_content = line.rstrip('\r\n')
            newline = line[len(line_content):]

            match = self.assignment_pattern.match(line_content)
            if not match:
                new_lines.append(line)
                continue

            prefix, key, eq, value, suffix = match.groups()
            if not value or "replace_me:" not in value:
                new_lines.append(line)
                continue

            new_value = self.placeholder_pattern.sub(
                lambda m: self.generator.generate(m.group(1)),
                value
            )
            new_lines.append(f"{prefix}{key}{eq}{new_value}{suffix or ''}{newline}")

        return "".join(new_lines)

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
        content = source_path.read_text(encoding="utf-8-sig")
        processor = EnvTemplateProcessor(SecretGenerator())
        new_content = processor.process(content)
        target_path.write_text(new_content, encoding="utf-8")
        print(f"Successfully generated: {target_path}")

    except Exception as e:
        raise RuntimeError(f"Failed to process environment file: {e}")

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
