"""Convert realesr-animevideov3.pth (SRVGGNetCompact, x4) to ONNX without PyTorch.

The .pth is a zip: data.pkl references tensor storages in data/<key>. A whitelisting
unpickler rebuilds them as numpy arrays (no arbitrary code from the pickle runs).
"""
import collections, pickle, zipfile, sys
import numpy as np
import onnx
from onnx import helper, numpy_helper, TensorProto

SRC, DST = sys.argv[1], sys.argv[2]
zf = zipfile.ZipFile(SRC)
root = zf.namelist()[0].split("/")[0]

DTYPES = {"FloatStorage": np.float32, "HalfStorage": np.float16}


class StorageType:
    def __init__(self, name): self.name = name


def rebuild_tensor_v2(storage, offset, size, stride, *_):
    dtype, key = storage
    raw = np.frombuffer(zf.read(f"{root}/data/{key}"), dtype=dtype)
    n = int(np.prod(size)) if size else 1
    arr = np.lib.stride_tricks.as_strided(
        raw[offset:], shape=size, strides=[s * raw.itemsize for s in stride]) if size else raw[offset:offset + 1]
    return np.array(arr, dtype=np.float32).reshape(size if size else ())


class SafeUnpickler(pickle.Unpickler):
    def find_class(self, module, name):
        if module == "collections" and name == "OrderedDict": return collections.OrderedDict
        if module == "torch._utils" and name == "_rebuild_tensor_v2": return rebuild_tensor_v2
        if module == "torch" and name in DTYPES: return StorageType(name)
        raise pickle.UnpicklingError(f"blocked {module}.{name}")

    def persistent_load(self, pid):
        # ('storage', StorageType, key, location, numel)
        _, stype, key, _, _ = pid
        return (DTYPES[stype.name], key)


obj = SafeUnpickler(zf.open(f"{root}/data.pkl")).load()
sd = obj.get("params", obj.get("params_ema", obj))
keys = sorted({int(k.split(".")[1]) for k in sd}, key=int)
print("layers:", len(keys), "first", sd["body.0.weight"].shape, "last", sd[f"body.{keys[-1]}.weight"].shape)

nodes, inits = [], []
cur = "input"
for i in keys:
    w = sd[f"body.{i}.weight"]
    if w.ndim == 4:  # conv 3x3, pad 1
        inits += [numpy_helper.from_array(w, f"w{i}"), numpy_helper.from_array(sd[f"body.{i}.bias"], f"b{i}")]
        nodes.append(helper.make_node("Conv", [cur, f"w{i}", f"b{i}"], [f"c{i}"], kernel_shape=[3, 3], pads=[1, 1, 1, 1]))
        cur = f"c{i}"
    else:  # PReLU, slope per channel
        inits.append(numpy_helper.from_array(w.reshape(-1, 1, 1), f"a{i}"))
        nodes.append(helper.make_node("PRelu", [cur, f"a{i}"], [f"p{i}"]))
        cur = f"p{i}"
nodes.append(helper.make_node("DepthToSpace", [cur], ["shuffled"], blocksize=4, mode="CRD"))
inits.append(numpy_helper.from_array(np.array([1, 1, 4, 4], np.float32), "scales"))
nodes.append(helper.make_node("Resize", ["input", "", "scales"], ["base"], mode="nearest"))
nodes.append(helper.make_node("Add", ["shuffled", "base"], ["output"]))

graph = helper.make_graph(
    nodes, "realesr_animevideov3",
    [helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3, "h", "w"])],
    [helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 3, "oh", "ow"])], inits)
model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)], producer_name="kiwami")
model.ir_version = 8
onnx.checker.check_model(model)
onnx.save(model, DST)
print("saved", DST)
