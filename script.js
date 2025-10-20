// src/main/resources/script.js
document.addEventListener('DOMContentLoaded', () => {
	// =================================================================
	// 1. DOM ELEMENT REFERENCES
	// =================================================================
	const orderListContainer = document.getElementById('orders-list');
	const listLoader = document.getElementById('list-loader');
	const detailContainer = document.getElementById('order-details-content');
	const itemsSection = document.getElementById('order-items-section');
	const itemsLoader = document.getElementById('items-loader');
	const itemsGrid = document.getElementById('order-items-grid');
	const welcomeMessage = document.getElementById('welcome-message');
	const detailLoader = document.getElementById('detail-loader');

	// -- Edit Modal Elements --
	const modal = document.getElementById('edit-po-modal');
	const modalCloseBtn = document.getElementById('modal-close-btn');
	const modalCancelBtn = document.getElementById('modal-cancel-btn');
	const modalSaveBtn = document.getElementById('modal-save-btn');
	const poInput = document.getElementById('po-input');
	const modalError = document.getElementById('modal-error');

	// -- Create Modal Elements --
	const newOrderBtn = document.getElementById('new-order-btn');
	const createModal = document.getElementById('create-order-modal');
	const createModalCloseBtn = document.getElementById('create-modal-close-btn');
	const createModalCancelBtn = document.getElementById('create-modal-cancel-btn');
	const createModalSaveBtn = document.getElementById('create-modal-save-btn');
	const createModalError = document.getElementById('create-modal-error');
	const soOrgInput = document.getElementById('so-org-input');
	const soDistInput = document.getElementById('so-dist-input');
	const soDivInput = document.getElementById('so-div-input');
	const soSoldToInput = document.getElementById('so-sold-to-input');
	const soMaterialInput = document.getElementById('so-material-input');
	const soQtyInput = document.getElementById('so-qty-input');


	//************************* */
	// -- Confirm Delete Modal Elements --
	const confirmModal = document.getElementById('confirm-delete-modal');
	const confirmModalCloseBtn = document.getElementById('confirm-modal-close-btn');
	const confirmModalCancelBtn = document.getElementById('confirm-modal-cancel-btn');
	const confirmModalDeleteBtn = document.getElementById('confirm-modal-delete-btn');
	const confirmModalText = document.getElementById('confirm-modal-text');

	// =================================================================
	// 2. STATE VARIABLES
	// =================================================================
	let currentOrderId = null;
	let currentETag = null;

	// =================================================================
	// 3. INITIALIZATION
	// =================================================================
	function init() {
		attachEventListeners();
		loadSalesOrderList();
	}

	// =================================================================
	// 4. DATA LOADING FUNCTIONS
	// =================================================================
	async function loadSalesOrderList() {
		showElement(listLoader);
		hideElement(orderListContainer);
		try {
			const response = await fetch('/api/sales-orders');
			if (!response.ok) throw new Error(`Fetch error: ${response.statusText}`);
			const xmlString = await response.text();
			const xmlDoc = new DOMParser().parseFromString(xmlString, "application/xml");
			const entries = xmlDoc.getElementsByTagName('entry');
			orderListContainer.innerHTML = '';
			if (entries.length === 0) {
				orderListContainer.innerHTML = '<p>No sales orders found.</p>';
				return;
			}
			for (const entry of entries) {
				const properties = entry.getElementsByTagName('m:properties')[0];
				const orderId = getTextContent(properties, 'd:SalesOrder');
				const orderType = getTextContent(properties, 'd:SalesOrderType');
				const org = getTextContent(properties, 'd:SalesOrganization');
				const listItem = document.createElement('li');
				listItem.className = 'order-item';
				listItem.dataset.orderId = orderId;
				listItem.innerHTML = `<div class="order-item-id">Order #${orderId}</div><div class="order-item-type">Type: ${orderType} | Org: ${org}</div>`;
				listItem.addEventListener('click', handleOrderItemClick);
				orderListContainer.appendChild(listItem);
			}
		} catch (error) {
			console.error('Error loading sales orders:', error);
			orderListContainer.innerHTML = `<p class="error-message">Could not load sales orders.</p>`;
		} finally {
			hideElement(listLoader);
			showElement(orderListContainer);
		}
	}

	async function loadSalesOrderDetail(orderId) {
		hideElement(welcomeMessage);
		hideElement(detailContainer);
		hideElement(itemsSection);
		showElement(detailLoader);
		currentETag = null;
		try {
			const response = await fetch(`/api/sales-order/${orderId}`);
			if (!response.ok) throw new Error(`Failed to fetch details: ${response.statusText}`);

			currentETag = response.headers.get('ETag');
			console.log("Captured ETag on frontend:", currentETag);

			const xmlString = await response.text();
			const xmlDoc = new DOMParser().parseFromString(xmlString, "application/xml");
			const properties = xmlDoc.getElementsByTagName('m:properties')[0];
			renderOrderDetails(properties);
			await loadSalesOrderItems(orderId);
		} catch (error) {
			console.error(`Error loading details for order ${orderId}:`, error);
			detailContainer.innerHTML = `<div class="error-message">Could not load details for Order #${orderId}.</div>`;
		} finally {
			hideElement(detailLoader);
			showElement(detailContainer);
		}
	}

	async function loadSalesOrderItems(orderId) {
		showElement(itemsSection);
		showElement(itemsLoader);
		itemsGrid.innerHTML = '';
		try {
			const response = await fetch(`/api/sales-order/${orderId}/items`);
			if (!response.ok) throw new Error(`Failed to fetch items: ${response.statusText}`);
			const xmlString = await response.text();
			const xmlDoc = new DOMParser().parseFromString(xmlString, "application/xml");
			const entries = xmlDoc.getElementsByTagName('entry');
			if (entries.length > 0) {
				renderOrderItems(entries);
			} else {
				itemsGrid.innerHTML = '<p>No line items found for this order.</p>';
			}
		} catch (error) {
			console.error(`Error loading items for order ${orderId}:`, error);
			itemsGrid.innerHTML = `<p class="error-message">Could not load items.</p>`;
		} finally {
			hideElement(itemsLoader);
		}
	}

	// =================================================================
	// 5. UI RENDERING & EVENT HANDLING
	// =================================================================
	function renderOrderDetails(properties) {
		const orderId = getTextContent(properties, 'd:SalesOrder');
		const purchaseOrderValue = getTextContent(properties, 'd:PurchaseOrderByCustomer');
		const detailsHTML = `
            <h2 class="detail-header">Order Details: <span>#${orderId}</span></h2>
            <div class="detail-form">
                ${createDetailRow('Order Type', getTextContent(properties, 'd:SalesOrderType'))}
                ${createDetailRow('Sales Organization', getTextContent(properties, 'd:SalesOrganization'))}
                ${createDetailRow('Distribution Channel', getTextContent(properties, 'd:DistributionChannel'))}
                ${createDetailRow('Organization Division', getTextContent(properties, 'd:OrganizationDivision'))}
                ${createDetailRow('Customer', getTextContent(properties, 'd:SoldToParty'))}
                ${createEditableDetailRow('Customer Purchase Order', purchaseOrderValue)}
                ${createDetailRow('Total Net Amount', `${getTextContent(properties, 'd:TotalNetAmount')} ${getTextContent(properties, 'd:TransactionCurrency')}`)}
                ${createDetailRow('Created By', getTextContent(properties, 'd:CreatedByUser'))}
            </div>`;
		detailContainer.innerHTML = detailsHTML;
		const editableField = detailContainer.querySelector('.editable');
		if (editableField) {
			editableField.addEventListener('click', openEditModal);
		}
	}

	// Locate this function
	function renderOrderItems(entries) {
		let cardsHTML = '';
		for (const entry of entries) {
			const properties = entry.getElementsByTagName('m:properties')[0];
			// NEW: Get the order and item IDs to use in the button
			const orderId = getTextContent(properties, 'd:SalesOrder');
			const itemId = getTextContent(properties, 'd:SalesOrderItem');
			const netAmount = parseFloat(getTextContent(properties, 'd:NetAmount')).toFixed(2);
			const quantity = parseFloat(getTextContent(properties, 'd:RequestedQuantity')).toFixed(2);
			cardsHTML += `
            <div class="item-card">
                <div class="item-card-header">
                    <span class="item-number">Item #${itemId}</span>
                    <span class="item-material">${getTextContent(properties, 'd:Material')}</span>
                    <button class="delete-item-btn" 
                            data-order-id="${orderId}" 
                            data-item-id="${itemId}" 
                            title="Delete Item">&times;</button>
                </div>
                <div class="item-card-body"><p class="item-description">${getTextContent(properties, 'd:SalesOrderItemText') || 'No description available.'}</p></div>
                <div class="item-card-footer">
                    <span class="item-quantity">Qty: ${quantity} ${getTextContent(properties, 'd:RequestedQuantityUnit')}</span>
                    <span class="item-value">${netAmount} ${getTextContent(properties, 'd:TransactionCurrency')}</span>
                </div>
            </div>`;
		}
		itemsGrid.innerHTML = cardsHTML;
	}

	function handleOrderItemClick(event) {
		const selectedItem = event.currentTarget;
		currentOrderId = selectedItem.dataset.orderId;
		const currentActive = document.querySelector('.order-item.active');
		if (currentActive) currentActive.classList.remove('active');
		selectedItem.classList.add('active');
		loadSalesOrderDetail(currentOrderId);
	}

	// =================================================================
	// 6. MODAL MANAGEMENT (EDIT)
	// =================================================================
	function openEditModal() {
		const currentValue = this.querySelector('.value-text').textContent;
		poInput.value = currentValue;
		modalError.style.display = 'none';
		modal.style.display = 'flex';
	}

	function closeEditModal() {
		modal.style.display = 'none';
	}

	async function savePurchaseOrder() {
		const newValue = poInput.value.trim();
		if (!newValue) {
			modalError.textContent = 'Purchase Order number cannot be empty.';
			modalError.style.display = 'block';
			return;
		}
		if (!currentETag) {
			modalError.textContent = 'Cannot save. Data version is missing. Please refresh.';
			modalError.style.display = 'block';
			return;
		}
		modalSaveBtn.disabled = true;
		modalSaveBtn.textContent = 'Saving...';
		try {
			const response = await fetch(`/api/sales-order/${currentOrderId}`, {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					"PurchaseOrderByCustomer": newValue,
					"etag": currentETag
				}),
			});
			if (!response.ok) throw new Error(`Server responded with status: ${response.status}`);
			await loadSalesOrderDetail(currentOrderId);
			closeEditModal();
		} catch (error) {
			console.error('Failed to update purchase order:', error);
			modalError.textContent = 'Failed to save changes. Please try again.';
			modalError.style.display = 'block';
		} finally {
			modalSaveBtn.disabled = false;
			modalSaveBtn.textContent = 'Save Changes';
		}
	}

	// =================================================================
	// 7. MODAL MANAGEMENT (CREATE)
	// =================================================================
	function openCreateModal() {
		if (createModalError) createModalError.style.display = 'none';
		createModal.style.display = 'flex';
	}

	function closeCreateModal() {
		createModal.style.display = 'none';
	}

	

	async function saveNewOrder() {
		const salesOrg = soOrgInput.value.trim();
		const distChannel = soDistInput.value.trim();
		const division = soDivInput.value.trim();
		const soldToParty = soSoldToInput.value.trim();
		const material = soMaterialInput.value.trim();
		const quantity = soQtyInput.value.trim();

		if (!salesOrg || !distChannel || !division || !soldToParty || !material || !quantity) {
			createModalError.textContent = 'All fields are required.';
			createModalError.style.display = 'block';
			return;
		}

		const newOrderData = {
			"SalesOrderType": "OR", "SalesOrganization": salesOrg, "DistributionChannel": distChannel,
			"OrganizationDivision": division, "SoldToParty": soldToParty,
			"PurchaseOrderByCustomer": "EPortalCreate_" + Date.now(),
			"to_Item": [{ "Material": material, "RequestedQuantity": quantity }]
		};

		createModalSaveBtn.disabled = true;
		createModalSaveBtn.textContent = 'Creating...';
		try {
			const response = await fetch('/api/sales-orders', {
				method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(newOrderData)
			});
			if (!response.ok) {
				const errorData = await response.json().catch(() => ({}));
				const errorMessage = errorData?.error?.message?.value || `Server error: ${response.statusText}`;
				throw new Error(errorMessage);
			}
			closeCreateModal();
			await loadSalesOrderList();
		} catch (error) {
			console.error('Failed to create sales order:', error);
			createModalError.textContent = `Error: ${error.message}`;
			createModalError.style.display = 'block';
		} finally {
			createModalSaveBtn.disabled = false;
			createModalSaveBtn.textContent = 'Create Order';
		}
	}


	/************ */
	// =================================================================
	// 8. MODAL MANAGEMENT (CONFIRM DELETE)
	// =================================================================
	function openConfirmModal(orderId, itemId) {
		// Set the text and store the IDs on the delete button for later use
		confirmModalText.textContent = `Are you sure you want to delete item #${itemId} from order #${orderId}?`;
		confirmModalDeleteBtn.dataset.orderId = orderId;
		confirmModalDeleteBtn.dataset.itemId = itemId;
		confirmModal.style.display = 'flex';
	}

	function closeConfirmModal() {
		confirmModal.style.display = 'none';
	}

	function handleDeleteConfirmation() {
		// Retrieve the IDs from the button and call the delete function
		const { orderId, itemId } = confirmModalDeleteBtn.dataset;
		deleteOrderItem(orderId, itemId);
		closeConfirmModal();
	}



	// =================================================================
	// 8. HELPER FUNCTIONS
	// =================================================================
	// CORRECTED TYPO: 'onst' is now 'const'
	const getTextContent = (element, tagName) => {
		const node = element.getElementsByTagName(tagName)[0];
		return node ? node.textContent : 'N/A';
	};
	const createDetailRow = (label, value) => `<div class="detail-label">${label}</div><div class="detail-value">${value}</div>`;
	const createEditableDetailRow = (label, value) => `
        <div class="detail-label">${label}</div>
        <div class="detail-value editable" data-field="PurchaseOrderByCustomer">
            <span class="value-text">${value}</span>
            <button class="change-btn">Change</button>
        </div>`;
	const showElement = (el) => { if (el) el.style.display = el.classList.contains('loader-container') ? 'flex' : 'block'; };
	const hideElement = (el) => { if (el) el.style.display = 'none'; };

	// =================================================================
	// 9. EVENT LISTENERS
	// =================================================================
	function attachEventListeners() {
		// Edit Modal
		if (modal) {
			modalCloseBtn.addEventListener('click', closeEditModal);
			modalCancelBtn.addEventListener('click', closeEditModal);
			modalSaveBtn.addEventListener('click', savePurchaseOrder);
			window.addEventListener('click', (event) => { if (event.target === modal) closeEditModal(); });
		}
		// Create Modal
		if (createModal) {
			newOrderBtn.addEventListener('click', openCreateModal);
			createModalCloseBtn.addEventListener('click', closeCreateModal);
			createModalCancelBtn.addEventListener('click', closeCreateModal);
			createModalSaveBtn.addEventListener('click', saveNewOrder);
			window.addEventListener('click', (event) => { if (event.target === createModal) closeCreateModal(); });
		}

		//***********************

		if (itemsGrid) {
			itemsGrid.addEventListener('click', (event) => {
				// Check if a delete button was the specific target of the click
				if (event.target.classList.contains('delete-item-btn')) {
					const button = event.target;
					const orderId = button.dataset.orderId;
					const itemId = button.dataset.itemId;

					// Show a confirmation dialog before proceeding
				openConfirmModal(orderId, itemId);

				}
			});
		}

if (confirmModal) {
            confirmModalCloseBtn.addEventListener('click', closeConfirmModal);
            confirmModalCancelBtn.addEventListener('click', closeConfirmModal);
            confirmModalDeleteBtn.addEventListener('click', handleDeleteConfirmation);
            window.addEventListener('click', (event) => { if (event.target === confirmModal) closeConfirmModal(); });
        }

	}


	// --- NEW: Function to handle the actual deletion ---
	async function deleteOrderItem(orderId, itemId, buttonElement) {
		// Use the ETag of the parent order for the lock check
		if (!currentETag) {
			alert("Cannot delete item: parent order's data version is missing. Please refresh.");
			return;
		}

		try {
			const response = await fetch(`/api/sales-order/${orderId}/item/${itemId}`, {
				method: 'DELETE',
				headers: {
					'If-Match': currentETag // Send the parent ETag for optimistic lock
				}
			});

			if (!response.ok) {
				throw new Error(`Server responded with status: ${response.status}`);
			}

			// Success: Remove the card from the UI for a smooth experience
			//buttonElement.closest('.item-card').remove();
			const cardToRemove = itemsGrid.querySelector(`.delete-item-btn[data-item-id='${itemId}']`).closest('.item-card');
			if (cardToRemove) {
				cardToRemove.remove();
			}




		} catch (error) {
			console.error('Failed to delete item:', error);
			alert('Failed to delete item. Please try again.');
		}
	}

	// =================================================================
	// 10. START APPLICATION
	// =================================================================
	init();
});