from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer, util
import json

app = Flask(__name__)
model = SentenceTransformer('all-MiniLM-L6-v2')

# Memory of past receipts
memory_db = []

@app.route('/store', methods=['POST'])
def store():
    data = request.json
    text = data.get("ocrText", "")
    embedding = model.encode(text, convert_to_tensor=True)
    memory_db.append({"text": text, "embedding": embedding})
    return jsonify({"message": "Stored successfully", "size": len(memory_db)})

@app.route('/retrieve', methods=['POST'])
def retrieve():
    query = request.json.get("query", "")
    query_vec = model.encode(query, convert_to_tensor=True)
    similarities = []
    for record in memory_db:
        score = util.pytorch_cos_sim(query_vec, record["embedding"]).item()
        similarities.append((record["text"], score))
    similarities.sort(key=lambda x: x[1], reverse=True)
    top_k = [t for t, s in similarities[:3]]
    return jsonify({"context": top_k})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=6060)
