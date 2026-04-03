package com.taqnid.batch.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.taqnid.batch.R
import com.taqnid.batch.databinding.FragmentScannerBinding
import com.taqnid.batch.ui.batch.BatchViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment de scan QR code avec CameraX + ML Kit Barcode Scanning.
 * Gère les permissions caméra et la prévisualisation en temps réel.
 */
class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by activityViewModels()
    private val batchViewModel: BatchViewModel by activityViewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Permission caméra
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else {
            Snackbar.make(binding.root, "Permission caméra refusée", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkCameraPermission()
        setupObservers()
        setupButtons()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> startCamera()

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // Prévisualisation
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        }

        // Analyse d'images pour la détection de QR codes
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                viewLifecycleOwner, cameraSelector, preview, imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e("ScannerFragment", "Erreur liaison caméra : ${e.message}")
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        // Ne traite que si en mode Idle (évite les traitements multiples)
        if (viewModel.scanResult.value !is ScanResult.Idle) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT || it.rawValue != null }
                    ?.rawValue?.let { content ->
                        viewModel.processQrCode(content)
                    }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun setupObservers() {
        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ScanResult.Idle -> {
                    binding.scanOverlay.setStatus(ScanOverlayView.Status.SCANNING)
                }
                is ScanResult.Scanning -> {
                    binding.scanOverlay.setStatus(ScanOverlayView.Status.PROCESSING)
                }
                is ScanResult.BatchFound -> {
                    binding.scanOverlay.setStatus(ScanOverlayView.Status.SUCCESS)
                    batchViewModel.selectBatch(result.batch.id)
                    // Naviguer vers le détail du lot après un court délai
                    binding.root.postDelayed({
                        findNavController().navigate(
                            R.id.action_scannerFragment_to_batchDetailFragment,
                            bundleOf("batchId" to result.batch.id)
                        )
                        viewModel.resetScan()
                    }, 600)
                }
                is ScanResult.TransferFound -> {
                    binding.scanOverlay.setStatus(ScanOverlayView.Status.SUCCESS)
                    binding.root.postDelayed({
                        findNavController().navigate(
                            R.id.action_scannerFragment_to_transferDetailFragment,
                            bundleOf("transferId" to result.transfer.id)
                        )
                        viewModel.resetScan()
                    }, 600)
                }
                is ScanResult.UnknownQR -> {
                    binding.scanOverlay.setStatus(ScanOverlayView.Status.ERROR)
                    Snackbar.make(
                        binding.root,
                        "QR code non reconnu : ${result.rawContent.take(30)}",
                        Snackbar.LENGTH_SHORT
                    ).setAction("Réessayer") { viewModel.resetScan() }.show()
                    viewModel.resetScan()
                }
                is ScanResult.Error -> {
                    binding.scanOverlay.setStatus(ScanOverlayView.Status.ERROR)
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT)
                        .setAction("OK") { viewModel.resetScan() }.show()
                    viewModel.resetScan()
                }
            }
        }

        viewModel.isFlashOn.observe(viewLifecycleOwner) { isOn ->
            camera?.cameraControl?.enableTorch(isOn)
            binding.btnFlash.setImageResource(
                if (isOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        }
    }

    private fun setupButtons() {
        binding.btnFlash.setOnClickListener {
            viewModel.toggleFlash()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
