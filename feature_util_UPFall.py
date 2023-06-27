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


def get_frames_with_gyro(df, frame_size, hop_size, training=False):
    N_FEATURES = 6
    frames = []
    labels = []

    # for i in range(0, len(df) - frame_size, hop_size):
    # for i in range(0, len(df), hop_size):
    for i in range(0, len(df), hop_size):
        if(i + frame_size > len(df)):
            break
        # x = df['x_ax'].values[i: i + frame_size]
        # y = df['y_ax'].values[i: i + frame_size]
        # z = df['z_ax'].values[i: i + frame_size]
        acc_x = df['acc_x'].values[i: i + frame_size]
        acc_y = df['acc_y'].values[i: i + frame_size]
        acc_z = df['acc_z'].values[i: i + frame_size]
        gyro_x = df['acc_x'].values[i: i + frame_size]
        gyro_y = df['acc_y'].values[i: i + frame_size]
        gyro_z = df['acc_z'].values[i: i + frame_size]

        frames.append([acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z])

        if training == True:
            label = stats.mode(df['outcome'][i: i + frame_size])[0][0]
            labels.append(label)

    frames = np.asarray(frames).reshape(-1, frame_size, N_FEATURES)
    # frames = np.asarray(frames)
    if training == True:
        labels = np.asarray(labels)
        return frames, labels

    return frames


def extract_features_with_gyro(frames):
    features = []
    for frame in frames:
        acc_x = frame[:, 0]
        acc_y = frame[:, 1]
        acc_z = frame[:, 2]
        gyro_x = frame[:, 3]
        gyro_y = frame[:, 4]
        gyro_z = frame[:, 5]

        mean_acc_x = np.mean(acc_x)
        mean_acc_y = np.mean(acc_y)
        mean_acc_z = np.mean(acc_z)
        std_acc_x = np.std(acc_x)
        std_acc_y = np.std(acc_y)
        std_acc_z = np.std(acc_z)
        kurtosis_acc_x = stats.kurtosis(acc_x)
        kurtosis_acc_y = stats.kurtosis(acc_y)
        kurtosis_acc_z = stats.kurtosis(acc_z)

        magnitudes_acc = np.sqrt(acc_x**2 + acc_y**2 + acc_z**2)
        max_magnitude_acc = magnitudes_acc.max()
        min_magnitude_acc = magnitudes_acc.min()

        mean_gyro_x = np.mean(gyro_x)
        mean_gyro_y = np.mean(gyro_y)
        mean_gyro_z = np.mean(gyro_z)
        std_gyro_x = np.std(gyro_x)
        std_gyro_y = np.std(gyro_y)
        std_gyro_z = np.std(gyro_z)
        kurtosis_gyro_x = stats.kurtosis(gyro_x)
        kurtosis_gyro_y = stats.kurtosis(gyro_y)
        kurtosis_gyro_z = stats.kurtosis(gyro_z)

        magnitudes_gyro = np.sqrt(gyro_x**2 + gyro_y**2 + gyro_z**2)
        max_magnitude_gyro = magnitudes_gyro.max()
        min_magnitude_gyro = magnitudes_gyro.min()
        # iqr_x = stats.iqr(frame[:, 0])
        # iqr_y = stats.iqr(frame[:, 1])
        # iqr_z = stats.iqr(frame[:, 2])

        features.append([mean_acc_x, mean_acc_y, mean_acc_z, std_acc_x, std_acc_y, std_acc_z, kurtosis_acc_x, kurtosis_acc_y,kurtosis_acc_z,
                         max_magnitude_acc, min_magnitude_acc, mean_gyro_x, mean_gyro_y, mean_gyro_z, std_gyro_x, std_gyro_y, std_gyro_z,
                         kurtosis_gyro_x, kurtosis_gyro_y, kurtosis_gyro_z, max_magnitude_gyro, min_magnitude_gyro])
    return np.array(features)

# Applies low-pass filter to dataframe. Default value of cutoff frequency is 5 Hz
def apply_filter(df, Fs, low_cutoff=5):

    # df_accelerometer = df[['x_ax', 'y_ax', 'z_ax']]

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
    mapping = {0: 'acc_x', 1: 'acc_y', 2: 'acc_z', 3: 'gyro_x', 4: 'gyro_y', 5: 'gyro_z'}
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
    # print(df.iloc[:,0:3].columns)
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