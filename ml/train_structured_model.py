from __future__ import annotations

import argparse
import json
import os
import random
from pathlib import Path

import numpy as np

from extractor.normalization import load_jsonl
from extractor.structured_format import build_header_prompt, build_header_target
from extractor.transformer_predictor import MODEL_CONFIG_FILE, STRUCTURED_MODEL_DIRNAME, resolve_device


def build_examples(rows: list[dict]) -> list[dict[str, str]]:
    examples = []
    for row in rows:
        prompt = build_header_prompt(row["input"])
        target = build_header_target(row["output"])
        examples.append({
            "id": row["id"],
            "prompt": prompt,
            "target": target,
        })
    return examples


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--base-model", default="google/flan-t5-small")
    parser.add_argument("--max-input-length", type=int, default=1536)
    parser.add_argument("--max-target-length", type=int, default=384)
    parser.add_argument("--per-device-batch-size", type=int, default=1)
    parser.add_argument("--gradient-accumulation-steps", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--epochs", type=float, default=10)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto", choices=("auto", "mps", "cpu"))
    parser.add_argument("--early-stopping-patience", type=int, default=2)
    args = parser.parse_args()

    os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
    os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

    import torch
    from datasets import Dataset
    from transformers import (
        AutoModelForSeq2SeqLM,
        AutoTokenizer,
        DataCollatorForSeq2Seq,
        EarlyStoppingCallback,
        Seq2SeqTrainer,
        Seq2SeqTrainingArguments,
    )

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    train_rows = load_jsonl(Path(args.splits_dir) / "train.jsonl")
    validation_rows = load_jsonl(Path(args.splits_dir) / "validation.jsonl")
    train_examples = build_examples(train_rows)
    validation_examples = build_examples(validation_rows)

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    model = AutoModelForSeq2SeqLM.from_pretrained(args.base_model)
    model.config.use_cache = False
    model.gradient_checkpointing_enable()

    device = resolve_device(args.device)
    model.to(device)

    train_dataset = Dataset.from_list(train_examples)
    validation_dataset = Dataset.from_list(validation_examples)

    def tokenize_batch(batch: dict[str, list[str]]) -> dict[str, list[int]]:
        model_inputs = tokenizer(
            batch["prompt"],
            max_length=args.max_input_length,
            truncation=True,
        )
        labels = tokenizer(
            text_target=batch["target"],
            max_length=args.max_target_length,
            truncation=True,
        )
        model_inputs["labels"] = labels["input_ids"]
        return model_inputs

    train_dataset = train_dataset.map(tokenize_batch, batched=True, remove_columns=train_dataset.column_names)
    validation_dataset = validation_dataset.map(tokenize_batch, batched=True, remove_columns=validation_dataset.column_names)

    structured_model_dir = Path(args.model_dir) / STRUCTURED_MODEL_DIRNAME
    structured_model_dir.mkdir(parents=True, exist_ok=True)

    training_args = Seq2SeqTrainingArguments(
        output_dir=str(structured_model_dir),
        do_train=True,
        do_eval=True,
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_strategy="steps",
        logging_steps=25,
        learning_rate=args.learning_rate,
        per_device_train_batch_size=args.per_device_batch_size,
        per_device_eval_batch_size=args.per_device_batch_size,
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        num_train_epochs=args.epochs,
        predict_with_generate=False,
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
        save_total_limit=2,
        seed=args.seed,
        data_seed=args.seed,
        report_to="none",
        dataloader_num_workers=0,
        dataloader_pin_memory=False,
        remove_unused_columns=True,
        optim="adafactor",
        gradient_checkpointing=True,
        torch_empty_cache_steps=1,
        use_cpu=device.type == "cpu",
    )

    trainer = Seq2SeqTrainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=validation_dataset,
        processing_class=tokenizer,
        data_collator=DataCollatorForSeq2Seq(tokenizer=tokenizer, model=model),
        callbacks=[EarlyStoppingCallback(early_stopping_patience=args.early_stopping_patience)],
    )

    trainer.train()
    trainer.save_model(structured_model_dir)
    tokenizer.save_pretrained(structured_model_dir)

    config_payload = {
        "base_model": args.base_model,
        "max_input_length": args.max_input_length,
        "max_target_length": args.max_target_length,
        "epochs": args.epochs,
        "learning_rate": args.learning_rate,
        "per_device_batch_size": args.per_device_batch_size,
        "gradient_accumulation_steps": args.gradient_accumulation_steps,
        "seed": args.seed,
        "device_preference": args.device,
    }
    (structured_model_dir / MODEL_CONFIG_FILE).write_text(json.dumps(config_payload, indent=2), encoding="utf-8")
    print(f"structured_model_saved={structured_model_dir}")


if __name__ == "__main__":
    main()
