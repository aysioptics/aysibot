package uz.kuponbot.kupon.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import uz.kuponbot.kupon.entity.Product;
import uz.kuponbot.kupon.repository.ProductRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public List<Product> getAllActiveProducts() {
        return productRepository.findByStatusOrderByCreatedAtDesc(Product.ProductStatus.ACTIVE);
    }
    
    public List<Product> getAvailableProducts() {
        return productRepository.findAvailableProducts();
    }
    
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }
    
    public Product save(Product product) {
        return productRepository.save(product);
    }
    
    public Product createProduct(String name, String description, String price, List<String> imageUrls, Integer stockQuantity) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new java.math.BigDecimal(price));
        product.setImageUrlsList(imageUrls); // Ko'p rasmlar
        product.setStockQuantity(stockQuantity);
        product.setStatus(Product.ProductStatus.ACTIVE);
        
        return productRepository.save(product);
    }
    
    public void updateStock(Long productId, Integer quantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            int newStock = product.getStockQuantity() - quantity;
            product.setStockQuantity(Math.max(0, newStock));
            
            if (newStock <= 0) {
                product.setStatus(Product.ProductStatus.OUT_OF_STOCK);
            }
            
            productRepository.save(product);
        }
    }
    
    public long getTotalProductsCount() {
        return productRepository.countActiveProducts();
    }
    
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}