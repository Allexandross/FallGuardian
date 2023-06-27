import socket
import json
import numpy as np
import pandas as pd
from joblib import load
import feature_util
import matplotlib.pyplot as plt
import seaborn as sns
from tensorflow.keras.models import load_model
import os

# HOST = "127.0.0.1"
HOST = "0.0.0.0"
PORT = 7896

# MODEL_PATH = 'New Models/rf_filtered_normalised.joblib'

Fs = 31.25
frame_size = int(Fs * 1)  # 31.25Hz and 1s window
hop_size = int(frame_size * 0.5)  # 50% overlap, i.e. 0.5s
receive_count = 0

def is_json(string):
    try:
        json.loads(string)
    except ValueError as e:
        return False
    return True

model_array = []

for folder in os.listdir('./Final Models/'):
    if ".joblib" in folder and ("exp1" in folder or "exp2" in folder):
        model_array.append(folder)

print("Choose one of the models to run:")
for i in range(len(model_array)):
    print(f"{i}: {model_array[i]}")

chosen_model = int(input("Enter number: "))

chosen_model = model_array[chosen_model]
print("Using", chosen_model)
if ".joblib" in chosen_model:
    model = load("./Final Models/" + chosen_model)

# load clf using joblib
# model = load(MODEL_PATH)
# print("Using", MODEL_PATH)

while True:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind((HOST, PORT))

        ip = socket.gethostbyname(socket.gethostname())
        print("Now listening on " + ip + ":" + str(PORT))
        s.listen()

        conn, addr = s.accept()
        print('Connected by', addr)

        data = b''
        while True:
            chunk = conn.recv(4096 * 500)
            if not chunk:
                break
            data += chunk

            # print(data)
            if(is_json(data) == False):
                continue

            json_data = json.loads(data.decode('utf-8'))
            df = pd.DataFrame(json_data)

            # downsampled_df = df[df.index % 3 == 0]
            # downsampled_df = downsampled_df[0:124]

            df = df.rename(mapper={"acc_x": "x_ax", "acc_y": "y_ax", "acc_z": "z_ax"}, axis=1)
            # print("len of df ", len(df))
            df_no_timestamp = df.drop(['timestamp'], axis=1)
            filtered_df = feature_util.apply_filter(df_no_timestamp)
            normalised_df = feature_util.normalise_df(filtered_df)
            frames = feature_util.get_frames(df_no_timestamp, frame_size, hop_size)
            # print("frames shape", frames.shape)
            features = feature_util.extract_features(frames)

            # for i in range(0, len(df), hop_size):
            #     if(i + frame_size > len(df)):
            #         break
            #     print("i: i + frame_size: ", i, i + frame_size)

            # print("features shape", len(features.shape))
            prediction = model.predict(features)
            receive_count += 1
            print(prediction)
            if(1 in prediction):
                print("Fall detected!")

            for window_probabilities in model.predict_proba(features):
                print("probability of window being fall: ",
                        window_probabilities[1])
            print(f"receive count: {receive_count}")

            # response = 'ACK' + '\n'  # send some response back to the client
            if 1 in prediction:
                response = 'fall\n'
                conn.sendall(response.encode())
                print("Sent response:", response)
            data = b''

        conn.close()
