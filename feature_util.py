import numpy as np
import scipy.stats as stats
from scipy import signal
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from imblearn.over_sampling import SMOTE
from imblearn.under_sampling import RandomUnderSampler
from sklearn.model_selection import KFold

import tensorflow as tf
from tensorflow.keras import Sequential
from tensorflow.keras.layers import Flatten, Dense, Dropout, BatchNormalization
from tensorflow.keras.layers import Conv2D, MaxPool2D
from tensorflow.keras.optimizers import Adam

def get_frames(df, frame_size, hop_size, training=False):
    N_FEATURES = 3
    frames = []
    labels = []

    for i in range(0, len(df) - frame_size, hop_size):
        x = df['x_ax'].values[i: i + frame_size]
        y = df['y_ax'].values[i: i + frame_size]
        z = df['z_ax'].values[i: i + frame_size]

        frames.append([x, y, z])

        if training == True:
            label = stats.mode(df['outcome'][i: i + frame_size])[0][0]
            labels.append(label)

    frames = np.asarray(frames).reshape(-1, frame_size, N_FEATURES)
    # frames = np.asarray(frames)
    if training == True:
        labels = np.asarray(labels)
        return frames, labels

    return frames



# def get_frames(df, frame_size, hop_size, training=False):
#     N_FEATURES = 3
#     frames = []
#     labels = []

#     for i in range(0, len(df), hop_size):
#         if(i + frame_size > len(df)):
#             break
#         x = df['x_ax'].values[i: i + frame_size]
#         y = df['y_ax'].values[i: i + frame_size]
#         z = df['z_ax'].values[i: i + frame_size]

#         frames.append([x, y, z])

#         if training == True:
#             label = stats.mode(df['outcome'][i: i + frame_size])[0][0]
#             labels.append(label)

#     frames = np.asarray(frames).reshape(-1, frame_size, N_FEATURES)
#     # frames = np.asarray(frames)
#     if training == True:
#         labels = np.asarray(labels)
#         return frames, labels

#     return frames


def extract_features(frames):
    features = []
    for frame in frames:
        x = frame[:, 0]
        y = frame[:, 1]
        z = frame[:, 2]

        mean_x = np.mean(frame[:, 0])
        mean_y = np.mean(frame[:, 1])
        mean_z = np.mean(frame[:, 2])
        std_x = np.std(frame[:, 0])
        std_y = np.std(frame[:, 1])
        std_z = np.std(frame[:, 2])
        kurtosis_x = stats.kurtosis(frame[:, 0])
        kurtosis_y = stats.kurtosis(frame[:, 1])
        kurtosis_z = stats.kurtosis(frame[:, 2])
        magnitudes = np.sqrt(x**2 + y**2 + z**2)
        max_magnitude = magnitudes.max()
        min_magnitude = magnitudes.min()
        # iqr_x = stats.iqr(frame[:, 0])
        # iqr_y = stats.iqr(frame[:, 1])
        # iqr_z = stats.iqr(frame[:, 2])

        features.append([mean_x, mean_y, mean_z, std_x, std_y, std_z, kurtosis_x,
                        # kurtosis_y, kurtosis_z, max_magnitude, min_magnitude, iqr_x, iqr_y, iqr_z])
                         kurtosis_y, kurtosis_z, max_magnitude, min_magnitude])
    return np.array(features)


# Applies low-pass filter to dataframe. Default value of cutoff frequency is 5 Hz
def apply_filter(df, low_cutoff=5):

    # df_accelerometer = df[['x_ax', 'y_ax', 'z_ax']]

    Fs = 31.25
    # low_cutoff = 5 # Cutoff frequency (Hz)
    print(f"low cutoff {low_cutoff} Hz")
    # high_cutoff = 0.1  # Cutoff frequency (Hz)
    order = 4  # Filter order

    nyq = 0.5 * Fs  # Nyquist Frequency
    normal_cutoff = low_cutoff / nyq

    # Apply low-pass filter
    b, a = signal.butter(order, normal_cutoff, btype='lowpass')
    data_filtered = signal.filtfilt(b, a, df, axis=0)

    # Apply high-pass filter
    # b, a = signal.butter(order, 2 * high_cutoff/Fs, btype='highpass')
    # data_filtered = signal.filtfilt(b, a, data_filtered, axis=0)

    df_filtered = pd.DataFrame(data_filtered)
    mapping = {0: "x_ax", 1: "y_ax", 2: "z_ax"}
    df_filtered = df_filtered.rename(columns=mapping)
    return df_filtered


# Standardises dataframe i.e. mean 0 and standard deviation 1
def standardise_df(df):
    # Standardise data
    df_standardised = (df - np.mean(df, axis=0)) / \
        np.std(df, axis=0)
    return df_standardised


# Normalises dataframe i.e. scales values to a range of 0 to 1
def normalise_df(df):
    print(df.iloc[:,0:3].columns)
    min_max = MinMaxScaler()
    data_scaled = min_max.fit_transform(df.values)
    df_scaled = pd.DataFrame(data_scaled, columns=df.columns)
    return df_scaled

def generate_windows_SMOTE(X, y):
    smote = SMOTE(random_state=42)
    X_reshaped = X.reshape(len(X), -1)
    X_resampled, y_resampled = smote.fit_resample(X_reshaped, y)
    X_resampled = X_resampled.reshape(X_resampled.shape[0], X.shape[1], X.shape[2])
    return X_resampled, y_resampled

def balance_windows_undersample(X, y):
    rus = RandomUnderSampler(random_state=42)
    X_reshaped = X.reshape(len(X), -1)
    X_resampled, y_resampled = rus.fit_resample(X_reshaped, y)
    X_resampled = X_resampled.reshape(X_resampled.shape[0], X.shape[1], X.shape[2])
    return X_resampled, y_resampled


def create_and_compile_cnn(input_shape):
    cnn_model = Sequential()
    cnn_model.add(Conv2D(16, (2, 2), activation='relu', input_shape=input_shape))
    cnn_model.add(Conv2D(32, (2, 2), activation='relu'))
    cnn_model.add(Flatten())
    cnn_model.add(Dense(64, activation='relu'))
    cnn_model.add(Dense(2, activation='softmax'))
    cnn_model.compile(optimizer=Adam(learning_rate=0.001), loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    return cnn_model

def cross_val_cnn(X, y, input_shape, epochs, num_folds=5):
    cv = KFold(n_splits=num_folds, shuffle=True, random_state=42)
    histories = []

    for train_index, val_index in cv.split(X):
        X_train, X_val = X[train_index], X[val_index]
        y_train, y_val = y[train_index], y[val_index]

        model = create_and_compile_cnn(input_shape)
        history = model.fit(X_train, y_train, epochs=epochs, validation_data=(X_val, y_val), verbose=0)
        
        histories.append(history.history)
        
    metrics = {}
    for key in histories[0].keys():
        metrics[key] = []
        for fold in histories:
            metrics[key].append(fold[key])
        metrics[key] = np.array(metrics[key])
        metrics[key + '_mean'] = np.mean(metrics[key], axis=0)
        metrics[key + '_std'] = np.std(metrics[key], axis=0)

    return metrics
    
def compute_cnn_cross_val_mean(metrics):
    epochs = 15
    val_accuracies = []
    for i in range(len(metrics['val_accuracy'])):
        val_accuracies.append(metrics['val_accuracy'][i][epochs - 1])
    print(np.mean(val_accuracies, axis=0))