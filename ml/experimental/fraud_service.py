from flask import Flask, request, jsonify
from sklearn.ensemble import IsolationForest
import numpy as np

app = Flask(__name__)

MODEL = None
FEATURE_HISTORY = []
MIN_SAMPLES = 20


def _fit_model():
    global MODEL
    if len(FEATURE_HISTORY) < MIN_SAMPLES:
        MODEL = None
        return
    X = np.array(FEATURE_HISTORY, dtype=float)
    MODEL = IsolationForest(
        n_estimators=200,
        contamination=0.1,
        random_state=42
    )
    MODEL.fit(X)


def _explain(current, history):
    if len(history) < 2:
        return "Insufficient history for explanation."
    X = np.array(history, dtype=float)
    mean = X.mean(axis=0)
    std = X.std(axis=0) + 1e-6
    z = np.abs((current - mean) / std)
    labels = ["amount", "tax_ratio", "vendor_frequency", "hour_of_day"]
    top = sorted(zip(labels, z.tolist()), key=lambda x: x[1], reverse=True)[:2]
    parts = [f"{name} is {score:.2f}σ from mean" for name, score in top]
    return "; ".join(parts)


@app.route("/score", methods=["POST"])
def score():
    data = request.get_json(force=True)
    amount = float(data.get("amount", 0.0))
    tax_ratio = float(data.get("tax_ratio", 0.0))
    vendor_frequency = float(data.get("vendor_frequency", 0.0))
    hour_of_day = float(data.get("hour_of_day", 0.0))

    features = np.array([amount, tax_ratio, vendor_frequency, hour_of_day], dtype=float)
    FEATURE_HISTORY.append(features.tolist())
    _fit_model()

    if MODEL is None:
        return jsonify({
            "anomaly": False,
            "score": 0.0,
            "reason": "Insufficient history; baseline score.",
            "explanation": _explain(features, FEATURE_HISTORY)
        })

    pred = MODEL.predict([features])[0]
    score = MODEL.decision_function([features])[0]

    return jsonify({
        "anomaly": pred == -1,
        "score": float(score),
        "reason": "Isolation Forest anomaly" if pred == -1 else "Normal pattern",
        "explanation": _explain(features, FEATURE_HISTORY)
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=7070)
