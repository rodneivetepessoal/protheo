<template>
  <div class="stl-viewer">
    <div ref="container" class="w-full h-96 bg-gray-900 rounded-lg relative">
      <div v-if="loading" class="absolute inset-0 flex items-center justify-center">
        <div class="text-white">Carregando modelo 3D...</div>
      </div>
      <div v-if="error" class="absolute inset-0 flex items-center justify-center">
        <div class="text-red-500">{{ error }}</div>
      </div>
    </div>
    
    <div class="mt-4 flex gap-2">
      <button @click="resetCamera" class="px-4 py-2 bg-gray-700 text-white rounded hover:bg-gray-600">
        Resetar Câmera
      </button>
      <button @click="toggleWireframe" class="px-4 py-2 bg-gray-700 text-white rounded hover:bg-gray-600">
        {{ wireframe ? 'Sólido' : 'Wireframe' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'

const props = defineProps({
  url: {
    type: String,
    required: true
  }
})

const container = ref(null)
const loading = ref(true)
const error = ref(null)
const wireframe = ref(false)

let scene, camera, renderer, controls, mesh

onMounted(() => {
  initScene()
  loadModel()
  animate()
  
  window.addEventListener('resize', onWindowResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  
  if (renderer) {
    renderer.dispose()
  }
})

watch(() => props.url, () => {
  loadModel()
})

function initScene() {
  // Scene
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x1a1a1a)
  
  // Camera
  const width = container.value.clientWidth
  const height = container.value.clientHeight
  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000)
  camera.position.set(0, 0, 100)
  
  // Renderer
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(window.devicePixelRatio)
  container.value.appendChild(renderer.domElement)
  
  // Controls
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  
  // Lights
  const ambientLight = new THREE.AmbientLight(0xffffff, 0.5)
  scene.add(ambientLight)
  
  const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
  directionalLight.position.set(1, 1, 1)
  scene.add(directionalLight)
  
  const directionalLight2 = new THREE.DirectionalLight(0xffffff, 0.5)
  directionalLight2.position.set(-1, -1, -1)
  scene.add(directionalLight2)
}

function loadModel() {
  loading.value = true
  error.value = null
  
  // Remover mesh anterior se existir
  if (mesh) {
    scene.remove(mesh)
  }
  
  const loader = new STLLoader()
  
  loader.load(
    props.url,
    (geometry) => {
      // Centralizar geometria
      geometry.center()
      
      // Material
      const material = new THREE.MeshPhongMaterial({
        color: 0x00a8ff,
        specular: 0x111111,
        shininess: 200,
        wireframe: wireframe.value
      })
      
      mesh = new THREE.Mesh(geometry, material)
      scene.add(mesh)
      
      // Ajustar câmera para enquadrar modelo
      const box = new THREE.Box3().setFromObject(mesh)
      const size = box.getSize(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z)
      const fov = camera.fov * (Math.PI / 180)
      let cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2))
      cameraZ *= 1.5 // Zoom out um pouco
      
      camera.position.set(cameraZ, cameraZ, cameraZ)
      camera.lookAt(0, 0, 0)
      controls.update()
      
      loading.value = false
    },
    (progress) => {
      // Progress callback
      console.log('Loading:', (progress.loaded / progress.total * 100) + '%')
    },
    (err) => {
      console.error('Error loading STL:', err)
      error.value = 'Erro ao carregar modelo 3D'
      loading.value = false
    }
  )
}

function animate() {
  requestAnimationFrame(animate)
  controls.update()
  renderer.render(scene, camera)
}

function onWindowResize() {
  const width = container.value.clientWidth
  const height = container.value.clientHeight
  
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

function resetCamera() {
  if (mesh) {
    const box = new THREE.Box3().setFromObject(mesh)
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)
    const fov = camera.fov * (Math.PI / 180)
    let cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2))
    cameraZ *= 1.5
    
    camera.position.set(cameraZ, cameraZ, cameraZ)
    camera.lookAt(0, 0, 0)
    controls.update()
  }
}

function toggleWireframe() {
  wireframe.value = !wireframe.value
  if (mesh) {
    mesh.material.wireframe = wireframe.value
  }
}
</script>

<style scoped>
.stl-viewer {
  width: 100%;
}
</style>
