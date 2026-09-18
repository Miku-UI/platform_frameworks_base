/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package android.app.ondeviceintelligence;

 import com.android.internal.infra.AndroidFuture;
 import android.os.ICancellationSignal;
 import android.os.ParcelFileDescriptor;
 import android.os.PersistableBundle;
 import android.os.RemoteCallback;
 import android.os.Bundle;
 import android.app.ondeviceintelligence.Feature;
 import android.app.ondeviceintelligence.FeatureDetails;
 import android.app.ondeviceintelligence.InferenceInfo;
 import android.app.ondeviceintelligence.Content;
 import java.util.List;
 import android.app.ondeviceintelligence.IDownloadCallback;
 import android.app.ondeviceintelligence.IListFeaturesCallback;
 import android.app.ondeviceintelligence.IFeatureCallback;
 import android.app.ondeviceintelligence.IFeatureDetailsCallback;
 import android.app.ondeviceintelligence.IResponseCallback;
import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.IProcessingSignal;
import android.app.ondeviceintelligence.ITokenInfoCallback;
import android.app.ondeviceintelligence.ILifecycleListener;
import android.app.ondeviceintelligence.embedding.IEmbeddingCallback;
import android.app.ondeviceintelligence.embedding.IEmbeddingModelCallback;
import android.app.ondeviceintelligence.embedding.IEmbeddingModelListCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionModelCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionModelListCallback;
import android.app.ondeviceintelligence.embedding.EmbeddingRequest;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionRequest;


/**
  * Interface for a OnDeviceIntelligenceManager for managing OnDeviceIntelligenceService and OnDeviceSandboxedInferenceService.
  *
  * @hide
  */
// Next available field id: 20
interface IOnDeviceIntelligenceManager {
      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void getVersion(in RemoteCallback remoteCallback) = 1;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void getFeature(in int featureId, in IFeatureCallback remoteCallback) = 2;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void listFeatures(in IListFeaturesCallback listFeaturesCallback) = 3;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_25Q4)")
      void listFeaturesWithFilter(in PersistableBundle featureParamsFilter,
            in IListFeaturesCallback listFeaturesCallback) = 11;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void getFeatureDetails(in Feature feature, in IFeatureDetailsCallback featureDetailsCallback) = 4;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void requestFeatureDownload(in Feature feature, in  AndroidFuture cancellationSignalFuture, in IDownloadCallback callback) = 5;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void requestTokenInfo(in Feature feature, in Bundle requestBundle, in  AndroidFuture cancellationSignalFuture,
                                                        in ITokenInfoCallback tokenInfocallback) = 6;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void processRequest(in Feature feature, in Bundle requestBundle, int requestType,
                                                in  AndroidFuture cancellationSignalFuture,
                                                in AndroidFuture processingSignalFuture,
                                                in IResponseCallback responseCallback) = 7;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      void processRequestStreaming(in Feature feature,
                    in Bundle requestBundle, int requestType, in  AndroidFuture cancellationSignalFuture,
                    in  AndroidFuture processingSignalFuture,
                    in IStreamingResponseCallback streamingCallback) = 8;

      String getRemoteServicePackageName() = 9;

      List<InferenceInfo> getLatestInferenceInfo(long startTimeEpochMillis) = 10;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_25Q4)")
      void registerInferenceServiceLifecycleListener(in ILifecycleListener listener) = 12;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_25Q4)")
      void unregisterInferenceServiceLifecycleListener(in ILifecycleListener listener) = 13;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void listEmbeddingModels(in IEmbeddingModelListCallback callback) = 14;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void fetchEmbeddingModel(in String modelSignature, in IEmbeddingModelCallback callback) = 15;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void fetchImageDescriptionModel(in String modelSignature, in IImageDescriptionModelCallback callback) = 16;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void listImageDescriptionModels(in IImageDescriptionModelListCallback callback) = 20;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void generateEmbeddings(in Feature feature, in EmbeddingRequest request, in AndroidFuture cancellationSignalFuture, in IEmbeddingCallback callback) = 17;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void generateImageDescription(in Feature feature, in ImageDescriptionRequest request, in AndroidFuture cancellationSignalFuture, in IImageDescriptionCallback callback) = 18;

      @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)")
      @JavaPassthrough(annotation="@android.annotation.FlaggedApi(android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)")
      void requestTokenInfoWithContent(in Feature feature, in Content content, in  AndroidFuture cancellationSignalFuture,
                                                                in ITokenInfoCallback tokenInfocallback) = 19;
 }
