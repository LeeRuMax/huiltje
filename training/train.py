"""Train het Dunstan-hoofd boven op YAMNet en exporteer alles voor de app.

Draait in tensorflow/tensorflow:2.18.0 met /work = deze map, /data = dataset, /models = YAMNet SavedModel:

    python train.py convert   # YAMNet -> yamnet_emb.tflite (1 venster van 15600 samples -> scores + embedding)
    python train.py features  # alle clips door de TFLite, per clip het gemiddelde van de vensters
    python train.py train     # evaluatie + dunstan_head.json voor de app

De app rekent met precies dezelfde TFLite en dezelfde vensters, dus training en app zien dezelfde getallen.
"""
import csv
import json
import sys
from pathlib import Path

import numpy as np

SR = 16000
WIN = 15600          # 0,975 s: één YAMNet-patch
HOP = 7680           # 0,48 s, gelijk aan YAMNet zelf
CLASSES = ['neh', 'owh', 'heh', 'eairh', 'eh']
BABY = [19, 20, 21]  # Crying/sobbing, Baby cry/infant cry, Whimper (yamnet_class_map.csv)
OUT = Path('/work/out')
DATA = Path('/data')


def convert():
    import tensorflow as tf
    yamnet = tf.saved_model.load('/models/yamnet')

    class Window(tf.Module):
        @tf.function(input_signature=[tf.TensorSpec([WIN], tf.float32)])
        def __call__(self, wave):
            scores, embeddings, _ = yamnet(wave)
            return {'scores': scores[0], 'embedding': embeddings[0]}

    m = Window()
    conv = tf.lite.TFLiteConverter.from_concrete_functions([m.__call__.get_concrete_function()], m)
    OUT.mkdir(exist_ok=True)
    (OUT / 'yamnet_emb.tflite').write_bytes(conv.convert())

    # Pariteit met het SavedModel op een willekeurig signaal.
    x = np.random.default_rng(0).uniform(-0.5, 0.5, WIN).astype(np.float32)
    ref_s, ref_e, _ = yamnet(x)
    s, e = Tflite().run(x)
    assert np.allclose(s, ref_s[0].numpy(), atol=1e-4) and np.allclose(e, ref_e[0].numpy(), atol=1e-4), 'TFLite wijkt af'
    print('yamnet_emb.tflite', (OUT / 'yamnet_emb.tflite').stat().st_size, 'bytes, pariteit ok')


class Tflite:
    def __init__(self):
        import tensorflow as tf
        self.it = tf.lite.Interpreter(model_path=str(OUT / 'yamnet_emb.tflite'))
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]['index']
        outs = {d['shape'][-1]: d['index'] for d in self.it.get_output_details()}
        self.s, self.e = outs[521], outs[1024]

    def run(self, window):
        self.it.set_tensor(self.inp, window)
        self.it.invoke()
        return self.it.get_tensor(self.s).copy(), self.it.get_tensor(self.e).copy()


def load(path):
    import soundfile as sf
    from scipy.signal import resample_poly
    x, sr = sf.read(path, dtype='float32', always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = np.gcd(sr, SR)
        x = resample_poly(x, SR // g, sr // g).astype(np.float32)
    return x


def windows(x):
    """Zelfde knip als de app: vensters van WIN met stap HOP, de laatste aangevuld met stilte."""
    if len(x) < WIN:
        x = np.pad(x, (0, WIN - len(x)))
    n = 1 + max(0, int(np.ceil((len(x) - WIN) / HOP)))
    x = np.pad(x, (0, (n - 1) * HOP + WIN - len(x)))
    return [x[i * HOP:i * HOP + WIN] for i in range(n)]


def features():
    t = Tflite()
    rows = list(csv.DictReader(open(DATA / 'metadata.csv')))
    folder = {'1': 'dunstan337', '2': 'collected1214'}
    X, y, tier, baby, names = [], [], [], [], []
    for r in rows:
        p = DATA / folder[r['tier']] / r['class_folder'] / r['filename']
        res = [t.run(w) for w in windows(load(p))]
        s = np.stack([a for a, _ in res])
        e = np.stack([b for _, b in res])
        X.append(e.mean(axis=0))
        baby.append(s[:, BABY].sum(axis=1).max())
        y.append(CLASSES.index(r['class_folder']))
        tier.append(int(r['tier']))
        names.append(r['filename'])
    np.savez(OUT / 'features.npz', X=np.array(X), y=np.array(y), tier=np.array(tier), baby=np.array(baby), names=np.array(names))
    b = np.array(baby)
    print(len(X), 'clips; babyscore percentielen 5/25/50:', np.percentile(b, [5, 25, 50]).round(3))


def model():
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler
    return make_pipeline(StandardScaler(), LogisticRegression(C=0.2, class_weight='balanced', max_iter=5000))


def score(m, X, y):
    from sklearn.metrics import balanced_accuracy_score, confusion_matrix, f1_score
    p = m.predict(X)
    return {'gebalanceerde_acc': round(balanced_accuracy_score(y, p), 3), 'macro_f1': round(f1_score(y, p, average='macro'), 3),
            'confusie (rij=echt, kolom=voorspeld, ' + '/'.join(CLASSES) + ')': confusion_matrix(y, p, labels=range(5)).tolist()}


def train():
    from sklearn.model_selection import StratifiedKFold, cross_val_predict
    from sklearn.metrics import balanced_accuracy_score, f1_score
    d = np.load(OUT / 'features.npz')
    X, y, tier = d['X'], d['y'], d['tier']
    # Het meegeleverde model leert alleen van de verzamelde clips (tier 2, CC BY 4.0). De knipsels uit Dunstans
    # eigen video's (tier 1, auteursrecht onzeker) dienen alleen als test en zitten niet in het model.
    t2 = tier == 2
    rep = {'clips_in_model': int(t2.sum()), 'per_klasse': {c: int((y[t2] == i).sum()) for i, c in enumerate(CLASSES)}}
    p = cross_val_predict(model(), X[t2], y[t2], cv=StratifiedKFold(5, shuffle=True, random_state=0))
    rep['5-voudig binnen de verzamelde clips (te rooskleurig: zelfde baby kan in train en test zitten)'] = {
        'gebalanceerde_acc': round(balanced_accuracy_score(y[t2], p), 3), 'macro_f1': round(f1_score(y[t2], p, average='macro'), 3)}
    for a, b in ((2, 1), (1, 2)):
        m = model().fit(X[tier == a], y[tier == a])
        rep[f'train tier {a} -> test tier {b} (andere bron, andere baby\'s)'] = score(m, X[tier == b], y[tier == b])
    rep['kans (altijd de grootste klasse)'] = round(1 / 5, 3)

    m = model().fit(X[t2], y[t2])
    sc, lr = m.named_steps['standardscaler'], m.named_steps['logisticregression']
    # Standaardisatie in de gewichten vouwen: logit = W·x + b, direct op de ruwe embedding.
    W = lr.coef_ / sc.scale_
    b = lr.intercept_ - (lr.coef_ * sc.mean_ / sc.scale_).sum(axis=1)
    assert np.allclose(X[t2] @ W.T + b, lr.decision_function(sc.transform(X[t2])), atol=1e-6)
    head = {'classes': CLASSES, 'W': W.round(7).tolist(), 'b': b.round(7).tolist(), 'baby_classes': BABY,
            'baby_threshold': float(np.percentile(d['baby'][t2], 5)), 'window': WIN, 'hop': HOP, 'sample_rate': SR}
    (OUT / 'dunstan_head.json').write_text(json.dumps(head))
    # Ijkpunten voor de Kotlin-unittest: drie embeddings met de kansen die het model hier geeft.
    Wr, br = np.array(head['W']), np.array(head['b'])
    golden = [{'emb': X[i].round(6).tolist(), 'probs': (lambda z: (np.exp(z - z.max()) / np.exp(z - z.max()).sum()).round(5).tolist())(X[i].round(6) @ Wr.T + br)}
              for i in np.flatnonzero(t2)[[0, 400, 1000]]]
    (OUT / 'golden.json').write_text(json.dumps(golden))
    (OUT / 'results.json').write_text(json.dumps(rep, indent=1, ensure_ascii=False))
    print(json.dumps(rep, indent=1, ensure_ascii=False))


if __name__ == '__main__':
    {'convert': convert, 'features': features, 'train': train}[sys.argv[1]]()
