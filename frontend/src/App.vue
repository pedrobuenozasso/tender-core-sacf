<script setup>
import { computed, reactive, ref } from 'vue'

const today = new Date()
const lastWeek = new Date(today)
lastWeek.setDate(today.getDate() - 7)

const form = reactive({
  mode: 'open',
  startDate: formatDateInput(lastWeek),
  endDate: formatDateInput(today),
  keywords: 'software\ntecnologia',
  blockedKeywords: '',
  states: '',
  maxResults: 100,
})

const loading = ref(false)
const error = ref('')
const response = ref(null)

const results = computed(() => response.value?.results || [])
const total = computed(() => response.value?.total || 0)

async function search() {
  loading.value = true
  error.value = ''
  response.value = null

  try {
    const res = await fetch('/api/tenders/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        mode: form.mode,
        startDate: form.startDate,
        endDate: form.endDate,
        keywords: splitLines(form.keywords).map(text => ({ text, qualifiers: [] })),
        blockedKeywords: splitLines(form.blockedKeywords),
        states: splitCsv(form.states),
        maxResults: Number(form.maxResults) || 100,
      }),
    })

    const data = await res.json()
    if (!res.ok) throw new Error(data.error || 'Search failed')
    response.value = data
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Search failed'
  } finally {
    loading.value = false
  }
}

function splitLines(value) {
  return value
    .split(/\n|,/)
    .map(item => item.trim())
    .filter(Boolean)
}

function splitCsv(value) {
  return value
    .split(',')
    .map(item => item.trim().toUpperCase())
    .filter(Boolean)
}

function formatDateInput(date) {
  return date.toISOString().slice(0, 10)
}

function formatCurrency(value) {
  if (value == null) return 'N/I'
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}
</script>

<template>
  <main class="app-shell">
    <section class="toolbar">
      <div>
        <p class="eyebrow">SACF Tender Core</p>
        <h1>PNCP search API</h1>
      </div>
      <a class="api-link" href="/api/health" target="_blank" rel="noreferrer">Health</a>
    </section>

    <section class="search-panel">
      <div class="field compact">
        <label>Mode</label>
        <select v-model="form.mode">
          <option value="open">Open tenders</option>
          <option value="awarded">Awarded contracts</option>
        </select>
      </div>

      <div class="field compact">
        <label>Start</label>
        <input v-model="form.startDate" type="date" />
      </div>

      <div class="field compact">
        <label>End</label>
        <input v-model="form.endDate" type="date" />
      </div>

      <div class="field compact">
        <label>UFs</label>
        <input v-model="form.states" placeholder="SP, RJ" />
      </div>

      <div class="field compact">
        <label>Limit</label>
        <input v-model="form.maxResults" min="1" max="1000" type="number" />
      </div>

      <div class="field wide">
        <label>Keywords</label>
        <textarea v-model="form.keywords" rows="4" />
      </div>

      <div class="field wide">
        <label>Blocked keywords</label>
        <textarea v-model="form.blockedKeywords" rows="4" placeholder="Optional" />
      </div>

      <button class="search-button" :disabled="loading" @click="search">
        {{ loading ? 'Searching...' : 'Search PNCP' }}
      </button>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="response" class="summary">
      <strong>{{ total }}</strong>
      <span>results for {{ response.mode }} between {{ response.startDate }} and {{ response.endDate }}</span>
    </section>

    <section class="results">
      <article v-for="item in results" :key="item.id" class="result-card">
        <header>
          <span class="tag">{{ item.type }}</span>
          <span class="state">{{ item.state || 'UF N/I' }}</span>
        </header>
        <h2>{{ item.object || 'Object unavailable' }}</h2>
        <p class="agency">{{ item.agency || 'Agency unavailable' }}</p>
        <dl>
          <div>
            <dt>Value</dt>
            <dd>{{ formatCurrency(item.estimatedValue) }}</dd>
          </div>
          <div>
            <dt>Publication</dt>
            <dd>{{ item.publicationDate || 'N/I' }}</dd>
          </div>
          <div>
            <dt>Closing</dt>
            <dd>{{ item.closingDate || 'N/I' }}</dd>
          </div>
          <div v-if="item.supplier">
            <dt>Supplier</dt>
            <dd>{{ item.supplier }}</dd>
          </div>
        </dl>
        <p class="keywords">{{ item.matchedKeywords.join(', ') }}</p>
        <a v-if="item.pncpLink" :href="item.pncpLink" target="_blank" rel="noreferrer">Open PNCP</a>
      </article>
    </section>
  </main>
</template>
